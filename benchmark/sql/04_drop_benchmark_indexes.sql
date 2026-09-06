-- Drops exactly the indexes that queries A/B (02/03) can use, to produce
-- the "without index" arm of the comparison. Everything else in the schema
-- (PK/UNIQUE indexes, other FK indexes) is left alone.
DROP INDEX IF EXISTS idx_locations_geom;
DROP INDEX IF EXISTS idx_workers_status;
DROP INDEX IF EXISTS idx_workers_location_id;
DROP INDEX IF EXISTS idx_worker_availability_worker_id;
DROP INDEX IF EXISTS idx_bookings_worker_id;
\echo 'Dropped: idx_locations_geom, idx_workers_status, idx_workers_location_id, idx_worker_availability_worker_id, idx_bookings_worker_id'
