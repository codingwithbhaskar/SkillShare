-- ============================================================================
-- SkillShare — Schema v3
-- 01_schema_v3.sql
--
-- 18 base tables + mv_worker_stats (materialized view, created in this file
-- but populated only once triggers/functions exist — see 04/05).
--
-- Depends on: btree_gist (for the EXCLUDE constraint), postgis (for the
-- geography(Point,4326) columns). Run this file first, before
-- 03_views_v3.sql, 04_triggers_v3.sql, 05_functions_procedures_v3.sql,
-- 06_seed_data_v3.sql.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS postgis;

-- ----------------------------------------------------------------------------
-- ENUM types
-- ----------------------------------------------------------------------------

CREATE TYPE user_role            AS ENUM ('admin', 'customer', 'worker');
CREATE TYPE account_status       AS ENUM ('active', 'inactive', 'suspended');
CREATE TYPE booking_status       AS ENUM ('pending', 'confirmed', 'in_progress', 'completed', 'cancelled');
CREATE TYPE booking_urgency      AS ENUM ('normal', 'urgent');
CREATE TYPE payment_method       AS ENUM ('cash', 'card', 'upi', 'wallet');
CREATE TYPE payment_status       AS ENUM ('pending', 'completed', 'failed', 'refunded');
CREATE TYPE proficiency_level    AS ENUM ('beginner', 'intermediate', 'expert');
CREATE TYPE notification_channel AS ENUM ('email', 'sms');
CREATE TYPE delivery_status      AS ENUM ('pending', 'sent', 'failed');
CREATE TYPE criteria_type        AS ENUM ('normal', 'urgent');
CREATE TYPE audit_action         AS ENUM ('INSERT', 'UPDATE', 'DELETE');

-- ----------------------------------------------------------------------------
-- A. Core actor & location
-- ----------------------------------------------------------------------------

CREATE TABLE locations (
    location_id   BIGSERIAL PRIMARY KEY,
    address_line  TEXT           NOT NULL,
    landmark      TEXT,
    city          VARCHAR(100)   NOT NULL,
    state         VARCHAR(100)   NOT NULL,
    pincode       VARCHAR(12),
    latitude      DECIMAL(9,6)   NOT NULL CHECK (latitude  BETWEEN -90  AND 90),
    longitude     DECIMAL(9,6)   NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    geom          geography(Point, 4326),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE users (
    user_id        BIGSERIAL PRIMARY KEY,
    role           user_role      NOT NULL,
    full_name      VARCHAR(150)   NOT NULL,
    email          VARCHAR(150)   NOT NULL UNIQUE,
    phone          VARCHAR(20),
    password_hash  VARCHAR(255)   NOT NULL,
    status         account_status NOT NULL DEFAULT 'active',
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE workers (
    worker_id           BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL UNIQUE REFERENCES users(user_id),
    location_id         BIGINT         REFERENCES locations(location_id),
    bio                 TEXT,
    experience_years    SMALLINT       NOT NULL DEFAULT 0 CHECK (experience_years >= 0),
    base_hourly_rate    NUMERIC(10,2)  CHECK (base_hourly_rate IS NULL OR base_hourly_rate > 0),
    status              account_status NOT NULL DEFAULT 'active',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE worker_availability (
    availability_id  BIGSERIAL PRIMARY KEY,
    worker_id        BIGINT   NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    day_of_week      SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6), -- 0=Sunday
    start_time       TIME     NOT NULL,
    end_time         TIME     NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_availability_time_order CHECK (end_time > start_time),
    CONSTRAINT uq_worker_availability_slot UNIQUE (worker_id, day_of_week, start_time, end_time)
);

-- Self-service "forgot password" flow (added V7). token_hash is the
-- SHA-256 (hex) of the random token emailed to the user - the plaintext
-- token is never stored. expires_at time-limits the link (30 min);
-- used_at makes it single-use. See V7__password_reset.sql for the full
-- rationale.
CREATE TABLE password_reset_tokens (
    token_id    BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);

-- ----------------------------------------------------------------------------
-- B. Skills & services (M:N)
-- ----------------------------------------------------------------------------

CREATE TABLE skills (
    skill_id     BIGSERIAL PRIMARY KEY,
    skill_name   VARCHAR(100) NOT NULL UNIQUE,
    -- Added V8 - lets the booking-creation form only offer skills
    -- relevant to the service already picked (e.g. Plumbing), instead of
    -- every skill in the catalogue. Nullable, same as services.category.
    category     VARCHAR(100),
    description  TEXT
);

CREATE TABLE worker_skills (
    worker_id          BIGINT NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    skill_id           BIGINT NOT NULL REFERENCES skills(skill_id)   ON DELETE CASCADE,
    proficiency_level  proficiency_level NOT NULL DEFAULT 'intermediate',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (worker_id, skill_id)
);

CREATE TABLE services (
    service_id    BIGSERIAL PRIMARY KEY,
    service_name  VARCHAR(150) NOT NULL UNIQUE,
    category      VARCHAR(100),
    description   TEXT
);

CREATE TABLE worker_services (
    worker_id     BIGINT NOT NULL REFERENCES workers(worker_id)  ON DELETE CASCADE,
    service_id    BIGINT NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    hourly_rate   NUMERIC(10,2) NOT NULL CHECK (hourly_rate > 0),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (worker_id, service_id)
);

-- ----------------------------------------------------------------------------
-- C. Bookings
-- ----------------------------------------------------------------------------

CREATE TABLE bookings (
    booking_id       BIGSERIAL PRIMARY KEY,
    customer_id      BIGINT   NOT NULL REFERENCES users(user_id),
    worker_id        BIGINT   REFERENCES workers(worker_id),        -- no ON DELETE CASCADE: booking
                                                                     -- history must survive a worker
                                                                     -- account being removed; deactivate
                                                                     -- via workers.status instead.
    service_id       BIGINT   NOT NULL REFERENCES services(service_id),
    location_id      BIGINT   NOT NULL REFERENCES locations(location_id),
    status           booking_status  NOT NULL DEFAULT 'pending',
    urgency          booking_urgency NOT NULL DEFAULT 'normal',
    scheduled_start  TIMESTAMPTZ NOT NULL,
    scheduled_end    TIMESTAMPTZ NOT NULL,
    booking_range    tstzrange GENERATED ALWAYS AS (
                          tstzrange(scheduled_start, scheduled_end, '[)')
                      ) STORED,
    total_amount     NUMERIC(10,2),
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_booking_time_order CHECK (scheduled_end > scheduled_start),
    -- Double-booking prevention at the constraint level: a given worker cannot
    -- hold two overlapping, still-live bookings. Cancelled bookings free the
    -- slot back up. Requires btree_gist for the "=" operator class on a
    -- plain bigint inside a GiST exclusion constraint.
    CONSTRAINT excl_worker_overlap EXCLUDE USING gist (
        worker_id WITH =,
        booking_range WITH &&
    ) WHERE (worker_id IS NOT NULL AND status <> 'cancelled')
);

CREATE TABLE booking_skills (
    booking_id  BIGINT NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    skill_id    BIGINT NOT NULL REFERENCES skills(skill_id),
    PRIMARY KEY (booking_id, skill_id)
);

-- ----------------------------------------------------------------------------
-- D. Payments & reviews
-- ----------------------------------------------------------------------------

CREATE TABLE payments (
    payment_id         BIGSERIAL PRIMARY KEY,
    booking_id         BIGINT NOT NULL UNIQUE REFERENCES bookings(booking_id),
    amount             NUMERIC(10,2)  NOT NULL CHECK (amount >= 0),
    method             payment_method,
    status             payment_status NOT NULL DEFAULT 'pending',
    -- Gateway-integration columns, added in 07 (Phase 6 / V6 migration):
    -- Razorpay's own ids for the order created before payment and the
    -- payment actually made, plus the HMAC signature Razorpay returns for
    -- verification - kept for audit trail even though it's re-verified at
    -- webhook time, never trusted from the value alone. All nullable: a
    -- payment row exists (status 'pending') before any of these are known.
    gateway_order_id   VARCHAR(64),
    gateway_payment_id VARCHAR(64),
    gateway_signature  VARCHAR(128),
    paid_at            TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reviews (
    review_id    BIGSERIAL PRIMARY KEY,
    booking_id   BIGINT NOT NULL UNIQUE REFERENCES bookings(booking_id),
    customer_id  BIGINT NOT NULL REFERENCES users(user_id),
    worker_id    BIGINT NOT NULL REFERENCES workers(worker_id),
    rating       SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- E. Notifications (outbox pattern)
-- ----------------------------------------------------------------------------

CREATE TABLE notifications (
    notification_id  BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(user_id),
    booking_id       BIGINT REFERENCES bookings(booking_id),
    type             VARCHAR(50) NOT NULL,
    title            VARCHAR(200) NOT NULL,
    message          TEXT,
    is_read          BOOLEAN NOT NULL DEFAULT false,
    read_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification_deliveries (
    delivery_id      BIGSERIAL PRIMARY KEY,
    notification_id  BIGINT NOT NULL REFERENCES notifications(notification_id) ON DELETE CASCADE,
    channel          notification_channel NOT NULL,
    status           delivery_status NOT NULL DEFAULT 'pending',
    attempt_count    INT NOT NULL DEFAULT 0,
    sent_at          TIMESTAMPTZ,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- F. Allocation / intelligent matching
-- ----------------------------------------------------------------------------

CREATE TABLE allocation_criteria (
    criteria_id        BIGSERIAL PRIMARY KEY,
    criteria_type      criteria_type NOT NULL UNIQUE,
    skill_weight       NUMERIC(3,2) NOT NULL,
    rating_weight      NUMERIC(3,2) NOT NULL,
    distance_weight    NUMERIC(3,2) NOT NULL,
    workload_weight    NUMERIC(3,2) NOT NULL,
    price_weight       NUMERIC(3,2) NOT NULL,
    experience_weight  NUMERIC(3,2) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_criteria_weights_sum CHECK (
        abs((skill_weight + rating_weight + distance_weight
             + workload_weight + price_weight + experience_weight) - 1.00) < 0.01
    )
);

CREATE TABLE allocation_log (
    log_id            BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    worker_id         BIGINT NOT NULL REFERENCES workers(worker_id),
    skill_score       NUMERIC(5,4),
    rating_score      NUMERIC(5,4),
    distance_score    NUMERIC(5,4),
    workload_score    NUMERIC(5,4),
    price_score       NUMERIC(5,4),
    experience_score  NUMERIC(5,4),
    total_score       NUMERIC(6,4),
    is_selected       BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- G. Platform / admin
-- ----------------------------------------------------------------------------

CREATE TABLE audit_log (
    audit_id     BIGSERIAL PRIMARY KEY,
    table_name   VARCHAR(100) NOT NULL,
    record_id    BIGINT,
    action       audit_action NOT NULL,
    old_data     JSONB,
    new_data     JSONB,
    changed_by   BIGINT REFERENCES users(user_id),
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE query_benchmark_results (
    benchmark_id        BIGSERIAL PRIMARY KEY,
    query_label         VARCHAR(150) NOT NULL,
    worker_count_scale  INT,
    used_index          BOOLEAN,
    estimated_cost       NUMERIC,
    actual_time_ms       NUMERIC,
    estimated_rows       BIGINT,
    actual_rows          BIGINT,
    explain_plan         JSONB,
    run_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- Indexes (named, beyond the implicit PK/UNIQUE ones above)
-- ----------------------------------------------------------------------------

CREATE INDEX idx_users_role                    ON users(role);
CREATE INDEX idx_locations_geom                ON locations USING GIST (geom);
CREATE INDEX idx_workers_location_id           ON workers(location_id);
CREATE INDEX idx_workers_status                ON workers(status);
CREATE INDEX idx_worker_availability_worker_id ON worker_availability(worker_id);
CREATE INDEX idx_bookings_customer_id          ON bookings(customer_id);
CREATE INDEX idx_bookings_worker_id            ON bookings(worker_id);
CREATE INDEX idx_bookings_status               ON bookings(status);
CREATE INDEX idx_bookings_location_id          ON bookings(location_id);
CREATE INDEX idx_notifications_user_id         ON notifications(user_id);
CREATE INDEX idx_notifications_unread          ON notifications(user_id) WHERE (is_read = false);
CREATE INDEX idx_notification_deliveries_status ON notification_deliveries(status);
CREATE INDEX idx_allocation_log_booking_id     ON allocation_log(booking_id);

-- ----------------------------------------------------------------------------
-- Materialized view: worker_stats
-- Pre-aggregate bookings and reviews SEPARATELY before joining to workers,
-- to avoid the row fan-out bug (2 bookings x 1 review = 2 joined rows)
-- caught during testing of an earlier draft.
-- ----------------------------------------------------------------------------

CREATE MATERIALIZED VIEW mv_worker_stats AS
SELECT
    w.worker_id,
    COALESCE(b.total_bookings, 0)      AS total_bookings,
    COALESCE(b.completed_bookings, 0)  AS completed_bookings,
    COALESCE(b.active_bookings, 0)     AS active_bookings,
    COALESCE(r.review_count, 0)        AS review_count,
    COALESCE(r.avg_rating, 0)          AS avg_rating
FROM workers w
LEFT JOIN (
    SELECT
        worker_id,
        COUNT(*)                                              AS total_bookings,
        COUNT(*) FILTER (WHERE status = 'completed')          AS completed_bookings,
        COUNT(*) FILTER (WHERE status IN ('confirmed','in_progress')) AS active_bookings
    FROM bookings
    WHERE worker_id IS NOT NULL
    GROUP BY worker_id
) b ON b.worker_id = w.worker_id
LEFT JOIN (
    SELECT worker_id, COUNT(*) AS review_count, AVG(rating)::NUMERIC(3,2) AS avg_rating
    FROM reviews
    GROUP BY worker_id
) r ON r.worker_id = w.worker_id;

CREATE UNIQUE INDEX uq_mv_worker_stats_worker_id ON mv_worker_stats(worker_id);
