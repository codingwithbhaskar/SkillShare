-- ============================================================================
-- cleanup_eval_data.sql
-- Deletes ALL synthetic evaluation-harness data (Step 11's
-- AllocationEvaluationSeeder output) from skillshare_dev, accumulated
-- across every past evaluation run in every session. Never touches real
-- user/worker/booking data, or Phase 9's test.alloc.* accounts.
--
-- Identifies eval rows purely by the tag AllocationEvaluationSeeder always
-- uses: user emails ending in '@skillshare.eval'.
--
-- Run this in pgAdmin's Query Tool (or psql) against skillshare_dev,
-- with the Spring Boot app NOT actively running an evaluation request at
-- the same time. Safe to re-run (no-ops if already clean).
-- ============================================================================

BEGIN;

-- --- 0. See what we're about to remove (informational only) ---
SELECT
    (SELECT count(*) FROM users WHERE email LIKE '%@skillshare.eval')                                   AS eval_users,
    (SELECT count(*) FROM workers WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval')) AS eval_workers,
    (SELECT count(*) FROM bookings
        WHERE customer_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval')
           OR worker_id IN (SELECT worker_id FROM workers WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval'))
    ) AS eval_bookings;

-- --- 1. Reviews tied to eval customers or eval workers ---
DELETE FROM reviews
WHERE customer_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval')
   OR worker_id IN (SELECT worker_id FROM workers WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval'));

-- --- 2. Notifications tied to eval users or eval bookings ---
--        (notification_deliveries cascades automatically via its own FK)
DELETE FROM notifications
WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval')
   OR booking_id IN (
        SELECT booking_id FROM bookings
        WHERE customer_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval')
           OR worker_id IN (SELECT worker_id FROM workers WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval'))
      );

-- --- 3. Payments, just in case any exist for eval bookings (shouldn't) ---
DELETE FROM payments
WHERE booking_id IN (
    SELECT booking_id FROM bookings
    WHERE customer_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval')
       OR worker_id IN (SELECT worker_id FROM workers WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval'))
);

-- --- 4. Bookings (booking_skills + allocation_log cascade automatically) ---
DELETE FROM bookings
WHERE customer_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval')
   OR worker_id IN (SELECT worker_id FROM workers WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval'));

-- --- 5. Workers (worker_availability/worker_skills/worker_services cascade automatically) ---
DELETE FROM workers
WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE '%@skillshare.eval');

-- --- 6. Orphaned eval locations (only ones no longer referenced by anything,
--        and only ones we recognize as eval-created, as a safety filter) ---
DELETE FROM locations
WHERE address_line LIKE 'Eval %'
  AND location_id NOT IN (SELECT location_id FROM workers WHERE location_id IS NOT NULL)
  AND location_id NOT IN (SELECT location_id FROM bookings);

-- --- 7. Users themselves ---
DELETE FROM users WHERE email LIKE '%@skillshare.eval';

-- --- 8. Refresh the worker-stats materialized view so deleted workers'
--        rows disappear from it too (fn_score_candidate reads this).
--        Plain (non-CONCURRENTLY) refresh - CONCURRENTLY cannot run
--        inside an explicit transaction block like this script uses. ---
REFRESH MATERIALIZED VIEW mv_worker_stats;

-- --- 9. Confirm zero remain ---
SELECT
    (SELECT count(*) FROM users WHERE email LIKE '%@skillshare.eval')  AS eval_users_remaining,
    (SELECT count(*) FROM locations WHERE address_line LIKE 'Eval %')  AS eval_locations_remaining;

COMMIT;
