-- ============================================================================
-- SkillShare benchmark DB — reference/lookup data only.
-- Run once, right after applying V1/V2/V3/V4/V6 to a fresh skillshare_benchmark
-- database. Deliberately does NOT seed users/workers/bookings (V5__seed.sql's
-- demo rows) — the synthetic generator (01_generate_synthetic_data.sql)
-- creates those at whatever scale you ask for.
-- ============================================================================

INSERT INTO allocation_criteria
    (criteria_type, skill_weight, rating_weight, distance_weight, workload_weight, price_weight, experience_weight)
VALUES
    ('normal', 0.30, 0.20, 0.20, 0.10, 0.15, 0.05),
    ('urgent', 0.30, 0.15, 0.15, 0.25, 0.05, 0.10)
ON CONFLICT (criteria_type) DO NOTHING;

INSERT INTO skills (skill_name, description) VALUES
    ('Wiring',              'Household electrical wiring'),
    ('Fuse Box Repair',     'Diagnosing and repairing fuse/breaker boxes'),
    ('Pipe Fitting',        'Fitting and joining plumbing pipes'),
    ('Drain Cleaning',      'Clearing blocked drains'),
    ('AC Repair',           'Air conditioner servicing and repair'),
    ('Furniture Assembly',  'Assembling flat-pack furniture')
ON CONFLICT (skill_name) DO NOTHING;

INSERT INTO services (service_name, category, description) VALUES
    ('Electrical Repair', 'Electrical', 'General household electrical repair work'),
    ('Plumbing',          'Plumbing',   'General household plumbing work'),
    ('Appliance Repair',  'Appliance',  'Repair of home appliances')
ON CONFLICT (service_name) DO NOTHING;

\echo 'Reference seed applied: 2 allocation_criteria, 6 skills, 3 services.'
