-- ============================================================================
-- SkillShare — Schema v3
-- 04_triggers_v3.sql
--
-- Depends on: 01_schema_v3.sql
-- Run before 05_functions_procedures_v3.sql (which calls fn_create_notification
-- indirectly via the same pattern) and before 06_seed_data_v3.sql.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- fn_set_updated_at — generic "touch updated_at" trigger function
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_locations_updated_at
    BEFORE UPDATE ON locations
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_workers_updated_at
    BEFORE UPDATE ON workers
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_bookings_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_allocation_criteria_updated_at
    BEFORE UPDATE ON allocation_criteria
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- fn_sync_location_geom — callers only ever write latitude/longitude;
-- geom is kept in step automatically.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_sync_location_geom()
RETURNS TRIGGER AS $$
BEGIN
    NEW.geom := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_locations_sync_geom
    BEFORE INSERT OR UPDATE OF latitude, longitude ON locations
    FOR EACH ROW EXECUTE FUNCTION fn_sync_location_geom();

-- ----------------------------------------------------------------------------
-- fn_audit_trigger — generic audit trigger, attached to several tables.
-- Reuses the plural-table-name PK-detection fix from advanced-dbms-extension-
-- v2: table_name || '_id' breaks for a table literally named "bookings"
-- (would look for "bookings_id" instead of "booking_id"), so the trailing
-- "s" is stripped first via regexp_replace(TG_TABLE_NAME, 's$', '').
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_audit_trigger()
RETURNS TRIGGER AS $$
DECLARE
    pk_column   TEXT := regexp_replace(TG_TABLE_NAME, 's$', '') || '_id';
    rec_id      BIGINT;
    old_json    JSONB;
    new_json    JSONB;
BEGIN
    IF TG_OP = 'DELETE' THEN
        old_json := to_jsonb(OLD);
        EXECUTE format('SELECT ($1).%I', pk_column) INTO rec_id USING OLD;
        INSERT INTO audit_log(table_name, record_id, action, old_data, new_data)
        VALUES (TG_TABLE_NAME, rec_id, 'DELETE', old_json, NULL);
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        old_json := to_jsonb(OLD);
        new_json := to_jsonb(NEW);
        EXECUTE format('SELECT ($1).%I', pk_column) INTO rec_id USING NEW;
        INSERT INTO audit_log(table_name, record_id, action, old_data, new_data)
        VALUES (TG_TABLE_NAME, rec_id, 'UPDATE', old_json, new_json);
        RETURN NEW;
    ELSIF TG_OP = 'INSERT' THEN
        new_json := to_jsonb(NEW);
        EXECUTE format('SELECT ($1).%I', pk_column) INTO rec_id USING NEW;
        INSERT INTO audit_log(table_name, record_id, action, old_data, new_data)
        VALUES (TG_TABLE_NAME, rec_id, 'INSERT', NULL, new_json);
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_users
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_audit_workers
    AFTER INSERT OR UPDATE OR DELETE ON workers
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_audit_bookings
    AFTER INSERT OR UPDATE OR DELETE ON bookings
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_audit_payments
    AFTER INSERT OR UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

CREATE TRIGGER trg_audit_reviews
    AFTER INSERT OR UPDATE OR DELETE ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_audit_trigger();

-- ----------------------------------------------------------------------------
-- fn_create_notification — shared helper. Writes the notifications row (the
-- in-app notification IS this row, no separate delivery row needed for the
-- in-app channel), then queues 'pending' notification_deliveries rows only
-- for the channels passed in.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_create_notification(
    p_user_id     BIGINT,
    p_booking_id  BIGINT,
    p_type        VARCHAR,
    p_title       VARCHAR,
    p_message     TEXT,
    p_channels    notification_channel[] DEFAULT ARRAY['email']::notification_channel[]
) RETURNS BIGINT AS $$
DECLARE
    v_notification_id BIGINT;
    v_channel notification_channel;
BEGIN
    INSERT INTO notifications(user_id, booking_id, type, title, message)
    VALUES (p_user_id, p_booking_id, p_type, p_title, p_message)
    RETURNING notification_id INTO v_notification_id;

    FOREACH v_channel IN ARRAY p_channels LOOP
        INSERT INTO notification_deliveries(notification_id, channel, status)
        VALUES (v_notification_id, v_channel, 'pending');
    END LOOP;

    RETURN v_notification_id;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- trg_notify_booking_status_change — fires no matter which code path changes
-- the status, so a notification can never be forgotten. Notifies the
-- customer always, and the worker when one is assigned.
-- Note: NEW.status is an ENUM; upper() has no enum overload, so an explicit
-- ::text cast is required (bug caught during testing of an earlier draft).
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_notify_booking_status_change()
RETURNS TRIGGER AS $$
DECLARE
    v_title   VARCHAR(200);
    v_message TEXT;
BEGIN
    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;

    IF NEW.status NOT IN ('confirmed', 'in_progress', 'completed', 'cancelled') THEN
        RETURN NEW;
    END IF;

    v_title   := 'Booking ' || upper(NEW.status::text);
    v_message := 'Your booking #' || NEW.booking_id || ' is now ' || NEW.status::text || '.';

    PERFORM fn_create_notification(
        NEW.customer_id, NEW.booking_id, 'BOOKING_' || upper(NEW.status::text),
        v_title, v_message, ARRAY['email','sms']::notification_channel[]
    );

    IF NEW.worker_id IS NOT NULL THEN
        PERFORM fn_create_notification(
            (SELECT user_id FROM workers WHERE worker_id = NEW.worker_id),
            NEW.booking_id, 'BOOKING_' || upper(NEW.status::text),
            v_title, v_message, ARRAY['email']::notification_channel[]
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notify_booking_status_change
    AFTER UPDATE OF status ON bookings
    FOR EACH ROW EXECUTE FUNCTION fn_notify_booking_status_change();

-- ----------------------------------------------------------------------------
-- trg_notify_review_received — notifies the worker when a review comes in.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_notify_review_received()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM fn_create_notification(
        (SELECT user_id FROM workers WHERE worker_id = NEW.worker_id),
        NEW.booking_id, 'REVIEW_RECEIVED', 'New review received',
        'You received a ' || NEW.rating || '-star review.',
        ARRAY['email']::notification_channel[]
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notify_review_received
    AFTER INSERT ON reviews
    FOR EACH ROW EXECUTE FUNCTION fn_notify_review_received();

-- ----------------------------------------------------------------------------
-- trg_notify_payment_received — notifies both customer and worker once a
-- payment settles.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_notify_payment_received()
RETURNS TRIGGER AS $$
DECLARE
    v_customer_id BIGINT;
    v_worker_user_id BIGINT;
BEGIN
    IF NEW.status <> 'completed' OR (TG_OP = 'UPDATE' AND OLD.status = 'completed') THEN
        RETURN NEW;
    END IF;

    SELECT b.customer_id, w.user_id
      INTO v_customer_id, v_worker_user_id
    FROM bookings b
    LEFT JOIN workers w ON w.worker_id = b.worker_id
    WHERE b.booking_id = NEW.booking_id;

    PERFORM fn_create_notification(
        v_customer_id, NEW.booking_id, 'PAYMENT_RECEIVED', 'Payment received',
        'Payment of ' || NEW.amount || ' for booking #' || NEW.booking_id || ' was received.',
        ARRAY['email']::notification_channel[]
    );

    IF v_worker_user_id IS NOT NULL THEN
        PERFORM fn_create_notification(
            v_worker_user_id, NEW.booking_id, 'PAYMENT_RECEIVED', 'Payment received',
            'Payment of ' || NEW.amount || ' for booking #' || NEW.booking_id || ' was received.',
            ARRAY['email']::notification_channel[]
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notify_payment_received
    AFTER INSERT OR UPDATE OF status ON payments
    FOR EACH ROW EXECUTE FUNCTION fn_notify_payment_received();

-- Note: the fourth notification event ("worker assigned") is NOT a trigger
-- here — it is inserted directly by sp_allocate_worker (see
-- 05_functions_procedures_v3.sql), since that procedure already owns the
-- moment of assignment.
