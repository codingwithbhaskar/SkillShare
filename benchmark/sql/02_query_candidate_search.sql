-- ============================================================================
-- Benchmark query A — candidate search. Same WHERE-clause as
-- fn_find_candidates(p_booking_id) in V4__functions_procedures.sql, just
-- with literal parameters instead of pulling them from a booking row, so it
-- can be EXPLAIN ANALYZE'd standalone at any data scale.
--
-- Fixed test parameters (must match 01_generate_synthetic_data.sql's
-- booking-window W):
--   service_id = 1 (Electrical Repair)
--   center     = 19.997454, 73.789803 (Nashik)
--   radius     = 30 km (fn_find_candidates' hardcoded constant)
--   window     = 2026-10-05 10:00-12:00 IST (a Monday -> day_of_week = 1)
--
-- Run with: psql ... -t -A -f 02_query_candidate_search.sql > out.json
-- (-t -A = tuples-only, unaligned, so stdout is exactly the JSON plan and
-- nothing else — see run_benchmark.ps1).
-- ============================================================================

EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT w.worker_id
FROM workers w
JOIN worker_services wsvc ON wsvc.worker_id = w.worker_id AND wsvc.service_id = 1
JOIN locations wl ON wl.location_id = w.location_id
WHERE w.status = 'active'
  AND ST_DWithin(
        wl.geom,
        ST_SetSRID(ST_MakePoint(73.789803, 19.997454), 4326)::geography,
        30000)
  AND EXISTS (
      SELECT 1 FROM worker_availability wa
      WHERE wa.worker_id = w.worker_id
        AND wa.day_of_week = 1
        AND wa.start_time <= TIME '10:00'
        AND wa.end_time   >= TIME '12:00'
  )
  AND NOT EXISTS (
      SELECT 1 FROM bookings ob
      WHERE ob.worker_id = w.worker_id
        AND ob.status <> 'cancelled'
        AND ob.booking_range && tstzrange(
              TIMESTAMPTZ '2026-10-05 10:00:00+05:30',
              TIMESTAMPTZ '2026-10-05 12:00:00+05:30',
              '[)')
  );
