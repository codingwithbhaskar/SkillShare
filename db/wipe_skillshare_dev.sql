-- ============================================================================
-- wipe_skillshare_dev.sql
-- Deletes EVERY ROW from EVERY TABLE in skillshare_dev, leaving the schema
-- (tables, indexes, functions, triggers, views) intact but completely empty.
-- This is IRREVERSIBLE and removes the seed data (demo workers, bookings 4/5,
-- etc.) along with everything else -- there is no undo once committed.
--
-- Run this in pgAdmin's Query Tool against skillshare_dev, with the Spring
-- Boot app STOPPED (not actively running/serving requests) first, so nothing
-- writes to these tables mid-truncate and nothing holds a lock on them.
-- ============================================================================

TRUNCATE TABLE
    reviews,
    notification_deliveries,
    notifications,
    payments,
    booking_skills,
    allocation_log,
    bookings,
    worker_availability,
    worker_skills,
    worker_services,
    workers,
    skills,
    services,
    locations,
    users,
    allocation_criteria,
    audit_log,
    query_benchmark_results
RESTART IDENTITY CASCADE;

-- mv_worker_stats is a materialized view, not a base table -- TRUNCATE
-- doesn't apply to it. Refresh it so it reflects the now-empty base tables
-- (it will simply end up with zero rows, not an error).
REFRESH MATERIALIZED VIEW mv_worker_stats;

-- Confirm every table is empty (all counts should read 0).
SELECT 'users' AS table_name, count(*) FROM users
UNION ALL SELECT 'workers', count(*) FROM workers
UNION ALL SELECT 'locations', count(*) FROM locations
UNION ALL SELECT 'skills', count(*) FROM skills
UNION ALL SELECT 'worker_skills', count(*) FROM worker_skills
UNION ALL SELECT 'services', count(*) FROM services
UNION ALL SELECT 'worker_services', count(*) FROM worker_services
UNION ALL SELECT 'worker_availability', count(*) FROM worker_availability
UNION ALL SELECT 'bookings', count(*) FROM bookings
UNION ALL SELECT 'booking_skills', count(*) FROM booking_skills
UNION ALL SELECT 'payments', count(*) FROM payments
UNION ALL SELECT 'reviews', count(*) FROM reviews
UNION ALL SELECT 'notifications', count(*) FROM notifications
UNION ALL SELECT 'notification_deliveries', count(*) FROM notification_deliveries
UNION ALL SELECT 'allocation_criteria', count(*) FROM allocation_criteria
UNION ALL SELECT 'allocation_log', count(*) FROM allocation_log
UNION ALL SELECT 'audit_log', count(*) FROM audit_log
UNION ALL SELECT 'query_benchmark_results', count(*) FROM query_benchmark_results
UNION ALL SELECT 'mv_worker_stats', count(*) FROM mv_worker_stats;
