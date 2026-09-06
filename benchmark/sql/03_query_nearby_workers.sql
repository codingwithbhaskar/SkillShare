-- ============================================================================
-- Benchmark query B — "workers near this point" radius search. Mirrors
-- WorkerRepository.findWorkersWithinRadius (backend/.../repository/
-- WorkerRepository.java), independent of any specific booking.
--
-- Fixed test parameters:
--   center = 19.997454, 73.789803 (Nashik)
--   radius = 15 km (the example radius in SpatialController's own javadoc)
--
-- Run with: psql ... -t -A -f 03_query_nearby_workers.sql > out.json
-- ============================================================================

EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT w.worker_id AS workerId,
       ST_Distance(wl.geom, ST_SetSRID(ST_MakePoint(73.789803, 19.997454), 4326)::geography) / 1000.0 AS distanceKm
FROM workers w
JOIN locations wl ON wl.location_id = w.location_id
WHERE w.status = 'active'
  AND ST_DWithin(wl.geom, ST_SetSRID(ST_MakePoint(73.789803, 19.997454), 4326)::geography, 15000)
ORDER BY distanceKm;
