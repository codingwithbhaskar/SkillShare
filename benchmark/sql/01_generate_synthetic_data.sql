-- ============================================================================
-- SkillShare benchmark DB — synthetic data generator.
--
-- Usage:  psql ... -v scale=1000 -f 01_generate_synthetic_data.sql
--
-- Wipes and regenerates workers/customers/bookings/etc at EXACTLY :scale
-- workers (proportional customers/locations/bookings around it). Reference
-- tables (skills, services, allocation_criteria — from 00_reference_seed.sql)
-- are left untouched.
--
-- Design notes:
--   - All primary keys are assigned EXPLICITLY (g, N+g, ...) rather than
--     relying on BIGSERIAL row-insertion order, so this is safe even if
--     Postgres parallelizes the generate_series scan. Sequences are
--     resynced with setval() at the end of each block.
--   - Workers/customers are scattered in a ~50km box around Nashik
--     (19.997454, 73.789803) — the same center used elsewhere in this
--     project — so a meaningful fraction fall outside the 30km hard-filter
--     radius fn_find_candidates uses. That's the point: it exercises the
--     spatial filter instead of trivially passing everyone.
--   - Every worker gets exactly 2 history bookings: one "past/completed"
--     booking (always) plus exactly one of "far-future/pending" or "the
--     benchmark window/confirmed" (mutually exclusive via g % 10). The
--     three candidate windows are placed in far-apart months so no worker
--     can ever collide with itself under the excl_worker_overlap EXCLUDE
--     constraint — different workers ARE
--     allowed to share a time window freely. 1 in 10 workers is booked
--     during the exact window 02_query_candidate_search.sql tests against,
--     so that query's NOT EXISTS (overlap) filter has real, ~10%-selective
--     work to do instead of being a no-op.
-- ============================================================================

\if :{?scale}
\else
  \echo 'ERROR: pass -v scale=<worker_count>, e.g.: psql ... -v scale=1000 -f 01_generate_synthetic_data.sql'
  \quit
\endif

\timing on
BEGIN;

-- ----------------------------------------------------------------------------
-- Wipe previous synthetic run. Reference tables (skills/services/
-- allocation_criteria) are NOT in this list, so they survive.
-- ----------------------------------------------------------------------------
TRUNCATE TABLE
    users, locations, workers, worker_availability, worker_skills,
    worker_services, bookings, booking_skills, payments, reviews,
    notifications, notification_deliveries, allocation_log
    RESTART IDENTITY CASCADE;

-- ----------------------------------------------------------------------------
-- Customers 1..N -> location_id = g, user_id = g
-- ----------------------------------------------------------------------------
INSERT INTO locations (location_id, address_line, city, state, pincode, latitude, longitude)
SELECT g,
       'Customer address ' || g,
       'Nashik', 'Maharashtra',
       '4221' || lpad((g % 90)::text, 2, '0'),
       19.997454 + ((random() - 0.5) * 0.9),
       73.789803 + ((random() - 0.5) * 0.9 * 1.064)
FROM generate_series(1, :scale) AS g;

INSERT INTO users (user_id, role, full_name, email, phone, password_hash)
SELECT g, 'customer', 'Bench Customer ' || g,
       'bench.customer.' || g || '@bench.local',
       '9' || lpad(g::text, 9, '0'),
       'bench_placeholder_hash'
FROM generate_series(1, :scale) AS g;

-- ----------------------------------------------------------------------------
-- Workers 1..N -> location_id = N+g, user_id = N+g, worker_id = g
-- ----------------------------------------------------------------------------
INSERT INTO locations (location_id, address_line, city, state, pincode, latitude, longitude)
SELECT :scale + g,
       'Worker address ' || g,
       'Nashik', 'Maharashtra',
       '4222' || lpad((g % 90)::text, 2, '0'),
       19.997454 + ((random() - 0.5) * 0.9),
       73.789803 + ((random() - 0.5) * 0.9 * 1.064)
FROM generate_series(1, :scale) AS g;

INSERT INTO users (user_id, role, full_name, email, phone, password_hash)
SELECT :scale + g, 'worker', 'Bench Worker ' || g,
       'bench.worker.' || g || '@bench.local',
       '8' || lpad(g::text, 9, '0'),
       'bench_placeholder_hash'
FROM generate_series(1, :scale) AS g;

INSERT INTO workers (worker_id, user_id, location_id, bio, experience_years, base_hourly_rate, status)
SELECT g, :scale + g, :scale + g,
       'Synthetic benchmark worker',
       (g % 15),
       (250 + (g % 40) * 10)::numeric,
       (CASE WHEN g % 20 = 0 THEN 'inactive' ELSE 'active' END)::account_status  -- ~5% inactive
FROM generate_series(1, :scale) AS g;

SELECT setval('locations_location_id_seq', 2 * :scale);
SELECT setval('users_user_id_seq', 2 * :scale);
SELECT setval('workers_worker_id_seq', :scale);

-- ----------------------------------------------------------------------------
-- Availability: every worker, every day, 08:00-20:00 (matches V5's demo
-- data — keeps the availability EXISTS filter cheap/trivially-true so the
-- benchmark isolates spatial + status + overlap filtering instead).
-- ----------------------------------------------------------------------------
INSERT INTO worker_availability (worker_id, day_of_week, start_time, end_time)
SELECT g, d, TIME '08:00', TIME '20:00'
FROM generate_series(1, :scale) AS g, generate_series(0, 6) AS d;

-- ----------------------------------------------------------------------------
-- Skills: 2 per worker, cycling deterministically through the 6 seeded
-- skills (no duplicates per worker since the two offsets are always
-- distinct mod 6).
-- ----------------------------------------------------------------------------
INSERT INTO worker_skills (worker_id, skill_id, proficiency_level)
SELECT g, (((g - 1) % 6) + 1),
       (ARRAY['beginner','intermediate','expert'])[1 + (g % 3)]::proficiency_level
FROM generate_series(1, :scale) AS g
UNION ALL
SELECT g, ((g % 6) + 1),
       (ARRAY['beginner','intermediate','expert'])[1 + ((g + 1) % 3)]::proficiency_level
FROM generate_series(1, :scale) AS g;

-- ----------------------------------------------------------------------------
-- Services offered: 1 per worker, cycling through the 3 seeded services.
-- This is the hard-filter column fn_find_candidates joins on.
-- ----------------------------------------------------------------------------
INSERT INTO worker_services (worker_id, service_id, hourly_rate)
SELECT g, (((g - 1) % 3) + 1), (250 + (g % 50) * 5)::numeric
FROM generate_series(1, :scale) AS g;

-- ----------------------------------------------------------------------------
-- Booking history: 3 fixed, non-overlapping (per worker) time windows.
--   P (past, completed)       - every worker
--   F (far future, pending)   - 90% of workers (g % 10 <> 0)
--   W (the benchmark window,  - 10% of workers (g % 10 = 0) -> these are
--      confirmed)               the ones 02_query_candidate_search.sql's
--                                NOT EXISTS(overlap) filter should exclude.
-- Booking window W must match the literal window hardcoded in
-- sql/02_query_candidate_search.sql (2026-10-05, 10:00-12:00 IST, a Monday).
-- ----------------------------------------------------------------------------
INSERT INTO bookings (customer_id, worker_id, service_id, location_id, status, urgency,
                       scheduled_start, scheduled_end, total_amount)
SELECT g, g, (((g - 1) % 3) + 1), g, 'completed', 'normal',
       TIMESTAMPTZ '2026-06-01 09:00:00+05:30',
       TIMESTAMPTZ '2026-06-01 11:00:00+05:30',
       (500 + (g % 40) * 10)::numeric
FROM generate_series(1, :scale) AS g;

INSERT INTO bookings (customer_id, worker_id, service_id, location_id, status, urgency,
                       scheduled_start, scheduled_end, total_amount)
SELECT g, g, (((g - 1) % 3) + 1), g, 'pending', 'normal',
       TIMESTAMPTZ '2026-12-01 09:00:00+05:30',
       TIMESTAMPTZ '2026-12-01 11:00:00+05:30',
       (500 + (g % 40) * 10)::numeric
FROM generate_series(1, :scale) AS g
WHERE g % 10 <> 0;

INSERT INTO bookings (customer_id, worker_id, service_id, location_id, status, urgency,
                       scheduled_start, scheduled_end, total_amount)
SELECT g, g, (((g - 1) % 3) + 1), g, 'confirmed', 'normal',
       TIMESTAMPTZ '2026-10-05 10:00:00+05:30',
       TIMESTAMPTZ '2026-10-05 12:00:00+05:30',
       (500 + (g % 40) * 10)::numeric
FROM generate_series(1, :scale) AS g
WHERE g % 10 = 0;

COMMIT;

-- Fresh planner stats — matters a lot for the with/without-index comparison.
ANALYZE users;
ANALYZE locations;
ANALYZE workers;
ANALYZE worker_availability;
ANALYZE worker_skills;
ANALYZE worker_services;
ANALYZE bookings;

\echo '--- Row counts at this scale ---'
SELECT 'users' AS table_name, count(*) FROM users
UNION ALL SELECT 'locations', count(*) FROM locations
UNION ALL SELECT 'workers', count(*) FROM workers
UNION ALL SELECT 'worker_availability', count(*) FROM worker_availability
UNION ALL SELECT 'worker_skills', count(*) FROM worker_skills
UNION ALL SELECT 'worker_services', count(*) FROM worker_services
UNION ALL SELECT 'bookings', count(*) FROM bookings;
