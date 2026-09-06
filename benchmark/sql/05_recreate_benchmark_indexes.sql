-- Recreates the indexes 04_drop_benchmark_indexes.sql removed, exactly as
-- defined in V1__schema.sql, then refreshes planner stats.
CREATE INDEX IF NOT EXISTS idx_locations_geom                ON locations USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_workers_status                ON workers(status);
CREATE INDEX IF NOT EXISTS idx_workers_location_id           ON workers(location_id);
CREATE INDEX IF NOT EXISTS idx_worker_availability_worker_id ON worker_availability(worker_id);
CREATE INDEX IF NOT EXISTS idx_bookings_worker_id             ON bookings(worker_id);
ANALYZE locations;
ANALYZE workers;
ANALYZE worker_availability;
ANALYZE bookings;
\echo 'Recreated: idx_locations_geom, idx_workers_status, idx_workers_location_id, idx_worker_availability_worker_id, idx_bookings_worker_id'
