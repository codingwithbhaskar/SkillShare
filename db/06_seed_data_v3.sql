-- ============================================================================
-- SkillShare — Schema v3
-- 06_seed_data_v3.sql
--
-- 3 services, 6 skills, 6 workers spread at varying distances (4 Nashik-area,
-- 2 deliberately far away in Mumbai/Pune to give the 30km radius filter
-- something real to exclude), 3 customers, 2 allocation_criteria rows
-- (normal/urgent), a mix of booking states (2 completed with payment+review,
-- 1 cancelled, 2 pending). The 2 pending bookings are allocated by actually
-- CALLing sp_allocate_worker at the end of this script, so allocation_log/
-- notifications reflect the real pipeline running on this data.
--
-- Depends on: 01_schema_v3.sql, 03_views_v3.sql, 04_triggers_v3.sql,
-- 05_functions_procedures_v3.sql.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Allocation criteria (weights sum to 1.00). Urgent bookings weight workload
-- higher and price lower than normal bookings.
-- ----------------------------------------------------------------------------

INSERT INTO allocation_criteria
    (criteria_type, skill_weight, rating_weight, distance_weight, workload_weight, price_weight, experience_weight)
VALUES
    ('normal', 0.30, 0.20, 0.20, 0.10, 0.15, 0.05),
    ('urgent', 0.30, 0.15, 0.15, 0.25, 0.05, 0.10);

-- ----------------------------------------------------------------------------
-- Skills & services
-- ----------------------------------------------------------------------------

INSERT INTO skills (skill_name, category, description) VALUES
    ('Wiring',              'Electrical',        'Household electrical wiring'),
    ('Fuse Box Repair',     'Electrical',        'Diagnosing and repairing fuse/breaker boxes'),
    ('Pipe Fitting',        'Plumbing',          'Fitting and joining plumbing pipes'),
    ('Drain Cleaning',      'Plumbing',          'Clearing blocked drains'),
    ('AC Repair',           'Appliance Repair',  'Air conditioner servicing and repair'),
    ('Furniture Assembly',  'Carpentry',         'Assembling flat-pack furniture');

INSERT INTO services (service_name, category, description) VALUES
    ('Electrical Repair', 'Electrical', 'General household electrical repair work'),
    ('Plumbing',          'Plumbing',   'General household plumbing work'),
    ('Appliance Repair',  'Appliance',  'Repair of home appliances');

-- ----------------------------------------------------------------------------
-- Locations: one job-site location in Nashik used by the two pending
-- bookings, plus one location per worker/customer.
-- ----------------------------------------------------------------------------

INSERT INTO locations (address_line, city, state, pincode, latitude, longitude) VALUES
    ('College Road, Nashik',            'Nashik', 'Maharashtra', '422005', 19.9975, 73.7898), -- 1: job site (bookings 4 & 5)
    ('Panchavati, Nashik',              'Nashik', 'Maharashtra', '422003', 20.0375, 73.7898), -- 2: Ravi   (~4.4km)
    ('Gangapur Road, Nashik',           'Nashik', 'Maharashtra', '422013', 20.0095, 73.7898), -- 3: Sunil  (~1.3km)
    ('Indira Nagar, Nashik',            'Nashik', 'Maharashtra', '422009', 20.0680, 73.8198), -- 4: Ganesh (~8.5km)
    ('Satpur, Nashik',                  'Nashik', 'Maharashtra', '422007', 19.9070, 73.7398), -- 5: Meera  (~11.4km)
    ('Andheri, Mumbai',                 'Mumbai', 'Maharashtra', '400058', 19.1197, 72.8468), -- 6: Pooja  (far, excluded)
    ('Shivaji Nagar, Pune',             'Pune',   'Maharashtra', '411005', 18.5304, 73.8467), -- 7: Anil   (far, excluded)
    ('MG Road, Nashik',                 'Nashik', 'Maharashtra', '422001', 19.9950, 73.7920), -- 8: customer Amit
    ('Nashik Road, Nashik',             'Nashik', 'Maharashtra', '422101', 19.9450, 73.8250), -- 9: customer Priya
    ('Deolali Camp, Nashik',            'Nashik', 'Maharashtra', '422401', 19.9450, 73.8350); -- 10: customer Rahul

-- ----------------------------------------------------------------------------
-- Users: 3 customers + 6 worker accounts
-- ----------------------------------------------------------------------------

INSERT INTO users (role, full_name, email, phone, password_hash) VALUES
    ('customer', 'Amit Deshmukh',  'amit.deshmukh@example.com',  '9900000001', 'placeholder_hash_1'),
    ('customer', 'Priya Joshi',    'priya.joshi@example.com',    '9900000002', 'placeholder_hash_2'),
    ('customer', 'Rahul Shah',     'rahul.shah@example.com',     '9900000003', 'placeholder_hash_3'),
    ('worker',   'Ravi Pawar',     'ravi.pawar@example.com',     '9900000011', 'placeholder_hash_4'),
    ('worker',   'Sunil Bhosale',  'sunil.bhosale@example.com',  '9900000012', 'placeholder_hash_5'),
    ('worker',   'Ganesh More',    'ganesh.more@example.com',    '9900000013', 'placeholder_hash_6'),
    ('worker',   'Meera Kale',     'meera.kale@example.com',     '9900000014', 'placeholder_hash_7'),
    ('worker',   'Pooja Rane',     'pooja.rane@example.com',     '9900000015', 'placeholder_hash_8'),
    ('worker',   'Anil Kulkarni',  'anil.kulkarni@example.com',  '9900000016', 'placeholder_hash_9');

-- ----------------------------------------------------------------------------
-- Workers (location_id 2-7 in the order inserted above)
-- ----------------------------------------------------------------------------

INSERT INTO workers (user_id, location_id, bio, experience_years, base_hourly_rate, status)
SELECT u.user_id, l.location_id, 'Experienced ' || u.full_name, exp.years, exp.rate, 'active'
FROM (VALUES
    ('ravi.pawar@example.com',    2, 8,  350),
    ('sunil.bhosale@example.com', 3, 4,  260),
    ('ganesh.more@example.com',   4, 6,  300),
    ('meera.kale@example.com',    5, 5,  400),
    ('pooja.rane@example.com',    6, 10, 320),
    ('anil.kulkarni@example.com', 7, 3,  270)
) AS exp(email, loc_idx, years, rate)
JOIN users u ON u.email = exp.email
JOIN locations l ON l.location_id = exp.loc_idx;

-- ----------------------------------------------------------------------------
-- Worker availability: every worker available every day, 08:00-20:00.
-- ----------------------------------------------------------------------------

INSERT INTO worker_availability (worker_id, day_of_week, start_time, end_time)
SELECT w.worker_id, d, TIME '08:00', TIME '20:00'
FROM workers w, generate_series(0, 6) AS d;

-- ----------------------------------------------------------------------------
-- Worker skills
-- ----------------------------------------------------------------------------

INSERT INTO worker_skills (worker_id, skill_id, proficiency_level)
SELECT w.worker_id, s.skill_id, x.level::proficiency_level
FROM (VALUES
    ('ravi.pawar@example.com',    'Wiring',             'expert'),
    ('ravi.pawar@example.com',    'Fuse Box Repair',    'expert'),
    ('sunil.bhosale@example.com', 'Wiring',             'intermediate'),
    ('sunil.bhosale@example.com', 'Pipe Fitting',       'intermediate'),
    ('ganesh.more@example.com',   'Pipe Fitting',       'expert'),
    ('ganesh.more@example.com',   'Drain Cleaning',     'intermediate'),
    ('meera.kale@example.com',    'AC Repair',          'expert'),
    ('meera.kale@example.com',    'Furniture Assembly', 'beginner'),
    ('pooja.rane@example.com',    'Wiring',             'expert'),
    ('pooja.rane@example.com',    'Fuse Box Repair',    'expert'),
    ('anil.kulkarni@example.com', 'Pipe Fitting',       'intermediate')
) AS x(email, skill_name, level)
JOIN users u ON u.email = x.email
JOIN workers w ON w.user_id = u.user_id
JOIN skills s ON s.skill_name = x.skill_name;

-- ----------------------------------------------------------------------------
-- Worker services (hourly rates)
-- ----------------------------------------------------------------------------

INSERT INTO worker_services (worker_id, service_id, hourly_rate)
SELECT w.worker_id, sv.service_id, x.rate
FROM (VALUES
    ('ravi.pawar@example.com',    'Electrical Repair', 350),
    ('sunil.bhosale@example.com', 'Electrical Repair', 280),
    ('sunil.bhosale@example.com', 'Plumbing',          250),
    ('ganesh.more@example.com',   'Plumbing',          300),
    ('meera.kale@example.com',    'Appliance Repair',  400),
    ('pooja.rane@example.com',    'Electrical Repair', 320),
    ('anil.kulkarni@example.com', 'Plumbing',          270)
) AS x(email, service_name, rate)
JOIN users u ON u.email = x.email
JOIN workers w ON w.user_id = u.user_id
JOIN services sv ON sv.service_name = x.service_name;

-- ----------------------------------------------------------------------------
-- Bookings: 2 completed (with payment + review), 1 cancelled, 2 pending
-- (allocated below via sp_allocate_worker).
-- ----------------------------------------------------------------------------

-- Booking 1 — completed, Meera did an Appliance Repair job for Rahul.
INSERT INTO bookings (customer_id, worker_id, service_id, location_id, status, urgency,
                       scheduled_start, scheduled_end, total_amount)
SELECT
    (SELECT user_id FROM users WHERE email = 'rahul.shah@example.com'),
    (SELECT worker_id FROM workers w JOIN users u ON u.user_id = w.user_id WHERE u.email = 'meera.kale@example.com'),
    (SELECT service_id FROM services WHERE service_name = 'Appliance Repair'),
    10, 'completed', 'normal',
    now() - INTERVAL '10 days' + TIME '10:00', now() - INTERVAL '10 days' + TIME '12:00', 800.00;

-- Booking 2 — completed, Ganesh did a Plumbing job for Amit.
INSERT INTO bookings (customer_id, worker_id, service_id, location_id, status, urgency,
                       scheduled_start, scheduled_end, total_amount)
SELECT
    (SELECT user_id FROM users WHERE email = 'amit.deshmukh@example.com'),
    (SELECT worker_id FROM workers w JOIN users u ON u.user_id = w.user_id WHERE u.email = 'ganesh.more@example.com'),
    (SELECT service_id FROM services WHERE service_name = 'Plumbing'),
    8, 'completed', 'normal',
    now() - INTERVAL '5 days' + TIME '09:00', now() - INTERVAL '5 days' + TIME '11:00', 600.00;

-- Booking 3 — cancelled, Ravi/Electrical for Priya.
INSERT INTO bookings (customer_id, worker_id, service_id, location_id, status, urgency,
                       scheduled_start, scheduled_end, total_amount)
SELECT
    (SELECT user_id FROM users WHERE email = 'priya.joshi@example.com'),
    (SELECT worker_id FROM workers w JOIN users u ON u.user_id = w.user_id WHERE u.email = 'ravi.pawar@example.com'),
    (SELECT service_id FROM services WHERE service_name = 'Electrical Repair'),
    9, 'cancelled', 'normal',
    now() - INTERVAL '3 days' + TIME '15:00', now() - INTERVAL '3 days' + TIME '16:00', NULL;

-- Booking 4 — PENDING, urgent, Electrical Repair needing Wiring + Fuse Box
-- Repair, at the shared job site. Candidates: Ravi (both skills, close),
-- Sunil (only Wiring, closer than Ravi), Pooja (both skills, but Mumbai —
-- excluded by the 30km radius filter).
INSERT INTO bookings (customer_id, worker_id, service_id, location_id, status, urgency,
                       scheduled_start, scheduled_end)
SELECT
    (SELECT user_id FROM users WHERE email = 'amit.deshmukh@example.com'),
    NULL,
    (SELECT service_id FROM services WHERE service_name = 'Electrical Repair'),
    1, 'pending', 'urgent',
    (CURRENT_DATE + INTERVAL '2 days' + TIME '10:00') AT TIME ZONE 'Asia/Kolkata',
    (CURRENT_DATE + INTERVAL '2 days' + TIME '12:00') AT TIME ZONE 'Asia/Kolkata';

INSERT INTO booking_skills (booking_id, skill_id)
SELECT b.booking_id, s.skill_id
FROM bookings b, skills s
WHERE b.urgency = 'urgent' AND b.status = 'pending'
  AND s.skill_name IN ('Wiring', 'Fuse Box Repair');

-- Booking 5 — PENDING, normal, Plumbing needing Pipe Fitting, at the same
-- job site. Candidates: Sunil and Ganesh both have Pipe Fitting; Anil
-- (Pune) is excluded by the 30km radius filter.
INSERT INTO bookings (customer_id, worker_id, service_id, location_id, status, urgency,
                       scheduled_start, scheduled_end)
SELECT
    (SELECT user_id FROM users WHERE email = 'priya.joshi@example.com'),
    NULL,
    (SELECT service_id FROM services WHERE service_name = 'Plumbing'),
    1, 'pending', 'normal',
    (CURRENT_DATE + INTERVAL '2 days' + TIME '14:00') AT TIME ZONE 'Asia/Kolkata',
    (CURRENT_DATE + INTERVAL '2 days' + TIME '15:00') AT TIME ZONE 'Asia/Kolkata';

INSERT INTO booking_skills (booking_id, skill_id)
SELECT b.booking_id, s.skill_id
FROM bookings b, skills s
WHERE b.urgency = 'normal' AND b.status = 'pending'
  AND s.skill_name = 'Pipe Fitting';

-- ----------------------------------------------------------------------------
-- Payments + reviews for the 2 completed bookings.
-- ----------------------------------------------------------------------------

INSERT INTO payments (booking_id, amount, method, status, paid_at)
SELECT b.booking_id, b.total_amount, 'upi', 'completed', b.scheduled_end
FROM bookings b WHERE b.status = 'completed';

INSERT INTO reviews (booking_id, customer_id, worker_id, rating, comment)
SELECT b.booking_id, b.customer_id, b.worker_id, x.rating, x.comment
FROM bookings b
JOIN (VALUES (1::bigint, 5, 'Excellent work, very professional.'),
             (2::bigint, 4, 'Good job, arrived a little late.')) AS x(booking_id, rating, comment)
  ON x.booking_id = b.booking_id;

-- ----------------------------------------------------------------------------
-- Refresh the materialized view so scoring (rating, workload) reflects the
-- data just inserted before allocation runs.
-- ----------------------------------------------------------------------------

REFRESH MATERIALIZED VIEW mv_worker_stats;

-- ----------------------------------------------------------------------------
-- Run the real allocation pipeline against the 2 pending bookings, so
-- allocation_log/notifications reflect the actual pipeline rather than
-- hand-inserted results.
-- ----------------------------------------------------------------------------

CALL sp_allocate_worker(4);
CALL sp_allocate_worker(5);

REFRESH MATERIALIZED VIEW mv_worker_stats;
