-- ============================================================================
-- bulk_seed_large.sql
--
-- Generates a large, realistic synthetic dataset for skillshare_dev:
--   50 skills, 100 services, 2000 customer users, 700 worker users
--   (+ worker_availability, worker_skills, worker_services), 1587 bookings
--   (+ booking_skills, payments, reviews).
--
-- Notifications and audit_log are populated AUTOMATICALLY by the schema's
-- existing triggers -- this script never inserts into notifications,
-- notification_deliveries or audit_log directly. It creates every booking
-- as 'pending' first, then UPDATEs it to its final status (exactly how
-- the real app does it via sp_allocate_worker), which is what fires
-- trg_notify_booking_status_change for real. Payments inserted with
-- status='completed' fire trg_notify_payment_received; reviews fire
-- trg_notify_review_received. All of it is the schema doing real work,
-- not fabricated notification rows.
--
-- Passwords: every generated user's password is "<FirstName>@123" (e.g.
-- Ravi's password is "Ravi@123"), hashed with pgcrypto's bcrypt
-- (crypt(..., gen_salt('bf'))) -- a real bcrypt hash Spring Security's
-- BCryptPasswordEncoder can verify at login, not a placeholder.
--
-- Emails: "<firstname>.<lastname><n>@gmail.com" where <n> is a running
-- counter, guaranteeing uniqueness even when two people share a name.
--
-- Account status: each user/worker gets a randomly-assigned status drawn
-- from a weighted pool covering all three account_status values (active/
-- inactive/suspended), so the admin panel's suspend/reactivate views have
-- real variety to show.
--
-- Safe to run on top of an already-wiped OR already-populated
-- skillshare_dev -- it only INSERTs new rows; skills/services that
-- already exist by name are skipped (ON CONFLICT DO NOTHING), and it
-- never deletes or truncates anything.
--
-- HOW TO RUN: open pgAdmin's Query Tool against skillshare_dev, paste
-- this whole file, and execute it as ONE script in a single session (the
-- temp tables used to pass ids between blocks only live for that one
-- session). Expect a couple of minutes -- this is several thousand real
-- INSERT/UPDATE statements, each firing the existing triggers. Progress
-- is reported via NOTICE messages (visible in pgAdmin's "Messages" tab).
--
-- Run the Spring Boot app STOPPED while this executes, same as any other
-- bulk DB script in this project, so nothing else is writing at the same
-- time.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- Step 1: Skills and Services (idempotent -- skips names that already exist)
-- ----------------------------------------------------------------------------

DO $$
DECLARE
    v_skill_names   TEXT[] := ARRAY['Electrical Wiring','Circuit Breaker Repair','Solar Panel Installation','Ceiling Fan Installation','Inverter Repair','Pipe Fitting','Leak Repair','Bathroom Fitting Installation','Water Tank Cleaning','Drainage Cleaning','Deep House Cleaning','Kitchen Deep Cleaning','Sofa Shampooing','Carpet Cleaning','Window Cleaning','Interior Wall Painting','Exterior Wall Painting','Waterproofing','Wood Polishing','Texture Painting','Furniture Assembly','Custom Carpentry','Door Repair','Cabinet Making','Wood Cutting','Lawn Mowing','Garden Maintenance','Tree Trimming','Landscaping','Plant Potting','AC Installation','AC Gas Refilling','Refrigerator Repair','Washing Machine Repair','Microwave Repair','Water Purifier Service','Pest Control - General','Termite Control','Cockroach Control','Rodent Control','Wall Tiling','Floor Tiling','Marble Polishing','Welding','Grill Fabrication','Roof Waterproofing','Roof Repair','Masonry Work','CCTV Installation','Smart Lock Installation']::TEXT[];
    v_service_names TEXT[] := ARRAY['Electrical Repair','Fan Installation','Switchboard Repair','Inverter & Battery Service','Home Wiring Overhaul','Solar Panel Setup','Doorbell Installation','MCB & Fuse Repair','Plumbing Repair','Tap & Faucet Installation','Bathroom Fitting','Water Tank Cleaning','Drainage Unclogging','Geyser Installation','Pipe Leakage Fix','Toilet Repair','Home Deep Cleaning','Kitchen Deep Cleaning','Bathroom Deep Cleaning','Sofa Cleaning','Carpet Cleaning','Window Cleaning','Move-in Move-out Cleaning','Office Cleaning','Post-Construction Cleaning','Interior Wall Painting','Exterior Wall Painting','Waterproof Coating','Wood Polishing','Texture Wall Design','Door & Window Painting','Furniture Assembly','Custom Wardrobe Making','Door Repair','Cabinet Installation','Wooden Flooring','Bed & Furniture Repair','Modular Kitchen Setup','Lawn Mowing','Garden Maintenance','Tree Trimming','Landscape Design','Potted Plant Setup','Terrace Garden Setup','AC Installation','AC Service & Gas Refill','Refrigerator Repair','Washing Machine Repair','Microwave Repair','Water Purifier Service','TV Mounting & Repair','Chimney Cleaning','General Pest Control','Termite Treatment','Cockroach Control','Rodent Control','Mosquito Fogging','Bed Bug Treatment','Wall Tiling','Floor Tiling','Marble Polishing','Home Renovation Consulting','False Ceiling Installation','Welding Work','Grill & Gate Fabrication','Staircase Railing','Roof Waterproofing','Roof Repair','Terrace Waterproofing','Masonry & Brickwork','Plaster Repair','Wall Crack Repair','CCTV Camera Installation','Smart Lock Installation','Video Doorbell Setup','Home Alarm Installation','Packing & Moving','Furniture Shifting','Vehicle Transport Assistance','Home Shifting - Full Service','Curtain & Blind Installation','Wallpaper Installation','False Wall Paneling','Home Decor Consulting','Water Heater Repair','Dishwasher Repair','Water Softener Installation','Septic Tank Cleaning','Balcony Waterproofing','Swimming Pool Cleaning','Sofa Reupholstery','Bookshelf Installation','Gate Automation Setup','Fire Alarm Installation','Generator Servicing','Stabilizer Installation','Wall Mount TV Unit','Kitchen Chimney Installation','Bathroom Renovation','Kitchen Renovation']::TEXT[];
    v_service_cats  TEXT[] := ARRAY['Electrical','Electrical','Electrical','Electrical','Electrical','Electrical','Electrical','Electrical','Plumbing','Plumbing','Plumbing','Plumbing','Plumbing','Plumbing','Plumbing','Plumbing','Cleaning','Cleaning','Cleaning','Cleaning','Cleaning','Cleaning','Cleaning','Cleaning','Cleaning','Painting','Painting','Painting','Painting','Painting','Painting','Carpentry','Carpentry','Carpentry','Carpentry','Carpentry','Carpentry','Carpentry','Gardening','Gardening','Gardening','Gardening','Gardening','Gardening','Appliance Repair','Appliance Repair','Appliance Repair','Appliance Repair','Appliance Repair','Appliance Repair','Appliance Repair','Appliance Repair','Pest Control','Pest Control','Pest Control','Pest Control','Pest Control','Pest Control','Renovation','Renovation','Renovation','Renovation','Renovation','Fabrication','Fabrication','Fabrication','Roofing','Roofing','Roofing','Construction','Construction','Construction','Home Security','Home Security','Home Security','Home Security','Moving','Moving','Moving','Moving','Interior','Interior','Interior','Interior','Appliance Repair','Appliance Repair','Plumbing','Plumbing','Roofing','Cleaning','Carpentry','Carpentry','Home Security','Home Security','Electrical','Electrical','Carpentry','Appliance Repair','Renovation','Renovation']::TEXT[];
    v_i INT;
BEGIN
    FOR v_i IN 1 .. array_length(v_skill_names, 1) LOOP
        INSERT INTO skills(skill_name, description)
        VALUES (v_skill_names[v_i], 'Skill: ' || v_skill_names[v_i])
        ON CONFLICT (skill_name) DO NOTHING;
    END LOOP;
    RAISE NOTICE 'Step 1a: % skills ensured', array_length(v_skill_names, 1);

    FOR v_i IN 1 .. array_length(v_service_names, 1) LOOP
        INSERT INTO services(service_name, category, description)
        VALUES (
            v_service_names[v_i], v_service_cats[v_i],
            v_service_names[v_i] || ' -- ' || v_service_cats[v_i] || ' service.'
        )
        ON CONFLICT (service_name) DO NOTHING;
    END LOOP;
    RAISE NOTICE 'Step 1b: % services ensured', array_length(v_service_names, 1);
END $$;

-- ----------------------------------------------------------------------------
-- Step 2: 2000 customer users -> tmp_customers(user_id, first_name)
-- ----------------------------------------------------------------------------

DROP TABLE IF EXISTS tmp_customers;
CREATE TEMP TABLE tmp_customers (user_id BIGINT, first_name TEXT);

DO $$
DECLARE
    v_first_names TEXT[] := ARRAY['Aarav','Vivaan','Aditya','Vihaan','Arjun','Sai','Reyansh','Krishna','Ishaan','Rohan','Aryan','Kunal','Nikhil','Rahul','Rajesh','Suresh','Amit','Sanjay','Vikram','Manoj','Prakash','Anil','Sunil','Ravi','Deepak','Ganesh','Mahesh','Sachin','Ajay','Vijay','Priya','Ananya','Diya','Ishita','Kavya','Meera','Neha','Pooja','Riya','Sneha','Anjali','Kirti','Sunita','Rekha','Manisha','Swati','Shalini','Nisha','Aarti','Divya','Rohit','Yash','Karan','Varun','Siddharth','Abhishek','Tejas','Omkar','Akash','Harsh']::TEXT[];
    v_last_names  TEXT[] := ARRAY['Sharma','Verma','Patil','Pawar','Bhosale','Deshmukh','Kulkarni','Joshi','Kale','Jadhav','Shinde','More','Gaikwad','Chavan','Nikam','Salunkhe','Kadam','Bhandari','Rane','Ghadge','Waghmare','Pandit','Thakur','Naik','Rao','Reddy','Gupta','Mehta','Shah','Iyer','Kapoor','Malhotra','Chauhan','Yadav','Mishra','Singh','Bhatt','Trivedi','Save','Kokate']::TEXT[];
    v_status_pool account_status[] := ARRAY[
        'active','active','active','active','active','active','active',
        'inactive','inactive','suspended'
    ]::account_status[];
    v_i INT;
    v_first TEXT;
    v_last TEXT;
    v_email TEXT;
    v_status account_status;
    v_user_id BIGINT;
BEGIN
    FOR v_i IN 1 .. 2000 LOOP
        v_first  := v_first_names[1 + floor(random() * array_length(v_first_names, 1))::int];
        v_last   := v_last_names[1 + floor(random() * array_length(v_last_names, 1))::int];
        v_email  := lower(v_first) || '.' || lower(v_last) || v_i || '@gmail.com';
        v_status := v_status_pool[1 + floor(random() * array_length(v_status_pool, 1))::int];

        INSERT INTO users(role, full_name, email, phone, password_hash, status)
        VALUES (
            'customer', v_first || ' ' || v_last, v_email,
            '9' || lpad(floor(random() * 999999999)::text, 9, '0'),
            crypt(v_first || '@123', gen_salt('bf')),
            v_status
        )
        RETURNING user_id INTO v_user_id;

        INSERT INTO tmp_customers(user_id, first_name) VALUES (v_user_id, v_first);

        IF v_i % 500 = 0 THEN RAISE NOTICE 'Step 2: % / 2000 customers', v_i; END IF;
    END LOOP;
    RAISE NOTICE 'Step 2 done: 2000 customers created';
END $$;

-- ----------------------------------------------------------------------------
-- Step 3: 700 worker users + workers + locations + availability + skills
--         + services -> tmp_workers, tmp_worker_services
-- ----------------------------------------------------------------------------

DROP TABLE IF EXISTS tmp_workers;
CREATE TEMP TABLE tmp_workers (worker_id BIGINT, user_id BIGINT, first_name TEXT);

DROP TABLE IF EXISTS tmp_worker_services;
CREATE TEMP TABLE tmp_worker_services (worker_id BIGINT, service_id BIGINT, hourly_rate NUMERIC(10,2));

DO $$
DECLARE
    v_first_names TEXT[] := ARRAY['Aarav','Vivaan','Aditya','Vihaan','Arjun','Sai','Reyansh','Krishna','Ishaan','Rohan','Aryan','Kunal','Nikhil','Rahul','Rajesh','Suresh','Amit','Sanjay','Vikram','Manoj','Prakash','Anil','Sunil','Ravi','Deepak','Ganesh','Mahesh','Sachin','Ajay','Vijay','Priya','Ananya','Diya','Ishita','Kavya','Meera','Neha','Pooja','Riya','Sneha','Anjali','Kirti','Sunita','Rekha','Manisha','Swati','Shalini','Nisha','Aarti','Divya','Rohit','Yash','Karan','Varun','Siddharth','Abhishek','Tejas','Omkar','Akash','Harsh']::TEXT[];
    v_last_names  TEXT[] := ARRAY['Sharma','Verma','Patil','Pawar','Bhosale','Deshmukh','Kulkarni','Joshi','Kale','Jadhav','Shinde','More','Gaikwad','Chavan','Nikam','Salunkhe','Kadam','Bhandari','Rane','Ghadge','Waghmare','Pandit','Thakur','Naik','Rao','Reddy','Gupta','Mehta','Shah','Iyer','Kapoor','Malhotra','Chauhan','Yadav','Mishra','Singh','Bhatt','Trivedi','Save','Kokate']::TEXT[];
    v_status_pool account_status[] := ARRAY[
        'active','active','active','active','active','active','active',
        'inactive','inactive','suspended'
    ]::account_status[];
    v_skill_ids   BIGINT[];
    v_service_ids BIGINT[];
    v_i INT;
    v_j INT;
    v_first TEXT;
    v_last TEXT;
    v_email TEXT;
    v_status account_status;
    v_user_id BIGINT;
    v_location_id BIGINT;
    v_worker_id BIGINT;
    v_num_skills INT;
    v_num_services INT;
    v_num_days INT;
    v_days INT[];
    v_picked_skill BIGINT;
    v_picked_service BIGINT;
    v_lat NUMERIC(9,6);
    v_lon NUMERIC(9,6);
BEGIN
    SELECT array_agg(skill_id) INTO v_skill_ids FROM skills;
    SELECT array_agg(service_id) INTO v_service_ids FROM services;

    FOR v_i IN 1 .. 700 LOOP
        v_first  := v_first_names[1 + floor(random() * array_length(v_first_names, 1))::int];
        v_last   := v_last_names[1 + floor(random() * array_length(v_last_names, 1))::int];
        v_email  := lower(v_first) || '.' || lower(v_last) || 'w' || v_i || '@gmail.com';
        v_status := v_status_pool[1 + floor(random() * array_length(v_status_pool, 1))::int];

        INSERT INTO users(role, full_name, email, phone, password_hash, status)
        VALUES (
            'worker', v_first || ' ' || v_last, v_email,
            '9' || lpad(floor(random() * 999999999)::text, 9, '0'),
            crypt(v_first || '@123', gen_salt('bf')),
            v_status
        )
        RETURNING user_id INTO v_user_id;

        -- Worker location: jittered around the Pune metro area, matching
        -- the rest of this project's existing seed data geography.
        v_lat := 18.45 + (random() * 0.30);   -- ~18.45 - 18.75
        v_lon := 73.75 + (random() * 0.30);   -- ~73.75 - 74.05

        INSERT INTO locations(address_line, landmark, city, state, pincode, latitude, longitude)
        VALUES (
            (10 + floor(random() * 990))::text || ', ' || v_last || ' Nagar',
            'Near ' || v_first || ' Chowk',
            'Pune', 'Maharashtra',
            '4110' || lpad(floor(random() * 99)::text, 2, '0'),
            v_lat, v_lon
        )
        RETURNING location_id INTO v_location_id;

        INSERT INTO workers(user_id, location_id, bio, experience_years, base_hourly_rate, status)
        VALUES (
            v_user_id, v_location_id,
            v_first || ' has ' || (floor(random() * 15))::text || ' years of hands-on field experience.',
            floor(random() * 16)::smallint,
            (150 + floor(random() * 1050))::numeric(10,2),
            v_status
        )
        RETURNING worker_id INTO v_worker_id;

        INSERT INTO tmp_workers(worker_id, user_id, first_name) VALUES (v_worker_id, v_user_id, v_first);

        -- Availability: 3-5 distinct days, one full-day slot each (keeps
        -- the UNIQUE(worker_id, day_of_week, start_time, end_time)
        -- constraint trivially satisfied).
        v_num_days := 3 + floor(random() * 3)::int;
        v_days := ARRAY(SELECT DISTINCT floor(random() * 7)::int FROM generate_series(1, v_num_days * 3) LIMIT v_num_days);
        FOR v_j IN 1 .. array_length(v_days, 1) LOOP
            INSERT INTO worker_availability(worker_id, day_of_week, start_time, end_time)
            VALUES (v_worker_id, v_days[v_j], '09:00', '18:00')
            ON CONFLICT DO NOTHING;
        END LOOP;

        -- Skills: 1-4 distinct skills.
        v_num_skills := 1 + floor(random() * 4)::int;
        FOR v_j IN 1 .. v_num_skills LOOP
            v_picked_skill := v_skill_ids[1 + floor(random() * array_length(v_skill_ids, 1))::int];
            INSERT INTO worker_skills(worker_id, skill_id, proficiency_level)
            VALUES (
                v_worker_id, v_picked_skill,
                (ARRAY['beginner','intermediate','intermediate','expert']::proficiency_level[])[1 + floor(random() * 4)::int]
            )
            ON CONFLICT DO NOTHING;
        END LOOP;

        -- Services offered: 1-3 distinct services, each with its own rate.
        v_num_services := 1 + floor(random() * 3)::int;
        FOR v_j IN 1 .. v_num_services LOOP
            v_picked_service := v_service_ids[1 + floor(random() * array_length(v_service_ids, 1))::int];
            INSERT INTO worker_services(worker_id, service_id, hourly_rate)
            VALUES (v_worker_id, v_picked_service, (150 + floor(random() * 1850))::numeric(10,2))
            ON CONFLICT DO NOTHING;
        END LOOP;

        INSERT INTO tmp_worker_services(worker_id, service_id, hourly_rate)
        SELECT worker_id, service_id, hourly_rate FROM worker_services WHERE worker_id = v_worker_id;

        IF v_i % 200 = 0 THEN RAISE NOTICE 'Step 3: % / 700 workers', v_i; END IF;
    END LOOP;
    RAISE NOTICE 'Step 3 done: 700 workers created (with availability/skills/services)';
END $$;

-- ----------------------------------------------------------------------------
-- Step 4: 1587 bookings (+ booking_skills, payments, reviews)
-- ----------------------------------------------------------------------------

DO $$
DECLARE
    v_target_bookings INT := 1587;
    v_i INT;
    v_customer_id BIGINT;
    v_service_id BIGINT;
    v_service_row RECORD;
    v_location_id BIGINT;
    v_booking_id BIGINT;
    v_start TIMESTAMPTZ;
    v_end   TIMESTAMPTZ;
    v_duration_hours INT;
    v_urgency booking_urgency;
    v_status_roll NUMERIC;
    v_final_status booking_status;
    v_worker_id BIGINT;
    v_hourly_rate NUMERIC(10,2);
    v_total_amount NUMERIC(10,2);
    v_attempt INT;
    v_assigned BOOLEAN;
    v_skill_ids BIGINT[];
    v_num_skills INT;
    v_j INT;
    v_lat NUMERIC(9,6);
    v_lon NUMERIC(9,6);
    v_payment_roll NUMERIC;
    v_payment_status payment_status;
    v_payment_method payment_method;
    v_review_roll NUMERIC;
    v_rating_roll NUMERIC;
    v_rating SMALLINT;
    v_comments TEXT[] := ARRAY[
        'Great work, very professional and on time.',
        'Did a solid job, would book again.',
        'Good service overall, minor delay in arrival.',
        'Excellent attention to detail, highly recommend.',
        'Average experience, work was okay.',
        'Very courteous and completed the job quickly.',
        'Not fully satisfied, had to follow up once.',
        'Outstanding service, exceeded expectations.',
        'Reliable and skilled, fair pricing too.',
        'Quick response and neat finish.'
    ];
    v_customer_count INT;
    v_service_count INT;
    v_worker_count INT;
BEGIN
    SELECT array_agg(skill_id) INTO v_skill_ids FROM skills;
    SELECT count(*) INTO v_customer_count FROM tmp_customers;
    SELECT count(*) INTO v_service_count FROM services;
    SELECT count(*) INTO v_worker_count FROM tmp_workers;

    FOR v_i IN 1 .. v_target_bookings LOOP
        SELECT user_id INTO v_customer_id FROM tmp_customers OFFSET floor(random() * v_customer_count) LIMIT 1;
        SELECT service_id, category INTO v_service_row FROM services OFFSET floor(random() * v_service_count) LIMIT 1;
        v_service_id := v_service_row.service_id;

        -- Booking's own service-address location (fresh row per booking,
        -- matching the app's own Phase 5 behavior).
        v_lat := 18.40 + (random() * 0.40);
        v_lon := 73.70 + (random() * 0.40);
        INSERT INTO locations(address_line, landmark, city, state, pincode, latitude, longitude)
        VALUES (
            (1 + floor(random() * 999))::text || ', Sector ' || (1 + floor(random() * 30))::text,
            'Near Landmark ' || (1 + floor(random() * 50))::text,
            'Pune', 'Maharashtra',
            '4110' || lpad(floor(random() * 99)::text, 2, '0'),
            v_lat, v_lon
        )
        RETURNING location_id INTO v_location_id;

        -- Schedule: somewhere between 90 days ago and 30 days from now.
        v_start := now() + ((floor(random() * 121) - 90) || ' days')::interval
                          + ((floor(random() * 10) + 8) || ' hours')::interval;
        v_duration_hours := 1 + floor(random() * 4)::int;
        v_end := v_start + (v_duration_hours || ' hours')::interval;
        v_urgency := CASE WHEN random() < 0.20 THEN 'urgent' ELSE 'normal' END;

        v_hourly_rate := (150 + floor(random() * 1350))::numeric(10,2);
        v_total_amount := v_hourly_rate * v_duration_hours;

        -- Insert as 'pending' with no worker -- mirrors POST /api/bookings,
        -- and keeps this INSERT completely outside the exclusion
        -- constraint (which only applies when worker_id IS NOT NULL).
        INSERT INTO bookings(
            customer_id, worker_id, service_id, location_id, status, urgency,
            scheduled_start, scheduled_end, total_amount, notes
        )
        VALUES (
            v_customer_id, NULL, v_service_id, v_location_id, 'pending', v_urgency,
            v_start, v_end, v_total_amount, 'Synthetic seed booking.'
        )
        RETURNING booking_id INTO v_booking_id;

        -- 1-3 required skills for this booking.
        v_num_skills := 1 + floor(random() * 3)::int;
        FOR v_j IN 1 .. v_num_skills LOOP
            INSERT INTO booking_skills(booking_id, skill_id)
            VALUES (v_booking_id, v_skill_ids[1 + floor(random() * array_length(v_skill_ids, 1))::int])
            ON CONFLICT DO NOTHING;
        END LOOP;

        -- Decide the final status this booking should land on.
        v_status_roll := random();
        v_final_status := CASE
            WHEN v_status_roll < 0.10 THEN 'pending'::booking_status
            WHEN v_status_roll < 0.25 THEN 'confirmed'::booking_status
            WHEN v_status_roll < 0.30 THEN 'in_progress'::booking_status
            WHEN v_status_roll < 0.90 THEN 'completed'::booking_status
            ELSE 'cancelled'::booking_status
        END;

        IF v_final_status = 'pending' THEN
            -- Leave as-is: no worker, no status change, no notification --
            -- matches a real not-yet-allocated booking.
            NULL;

        ELSIF v_final_status = 'cancelled' THEN
            -- Cancelled bookings never collide with the exclusion
            -- constraint (it only applies WHERE status <> 'cancelled'),
            -- so no retry loop is needed here.
            IF random() < 0.5 THEN
                SELECT worker_id, hourly_rate INTO v_worker_id, v_hourly_rate
                FROM tmp_worker_services WHERE service_id = v_service_id
                ORDER BY random() LIMIT 1;
            ELSE
                v_worker_id := NULL;
            END IF;
            UPDATE bookings SET worker_id = v_worker_id, status = 'cancelled'
            WHERE booking_id = v_booking_id;

        ELSE
            -- confirmed / in_progress / completed: needs a real worker
            -- assignment that doesn't overlap that worker's existing
            -- schedule. Retry a few times with a different candidate
            -- before giving up and leaving the booking pending.
            v_assigned := FALSE;
            FOR v_attempt IN 1 .. 5 LOOP
                SELECT worker_id, hourly_rate INTO v_worker_id, v_hourly_rate
                FROM tmp_worker_services WHERE service_id = v_service_id
                ORDER BY random() LIMIT 1;

                IF v_worker_id IS NULL THEN
                    SELECT worker_id INTO v_worker_id FROM tmp_workers OFFSET floor(random() * v_worker_count) LIMIT 1;
                END IF;

                BEGIN
                    UPDATE bookings
                    SET worker_id = v_worker_id, status = v_final_status,
                        total_amount = COALESCE(v_hourly_rate, v_total_amount / v_duration_hours) * v_duration_hours
                    WHERE booking_id = v_booking_id;
                    v_assigned := TRUE;
                    EXIT;
                EXCEPTION WHEN exclusion_violation THEN
                    -- This worker's schedule collides with an already-
                    -- assigned booking; try another candidate.
                    CONTINUE;
                END;
            END LOOP;

            IF NOT v_assigned THEN
                -- Couldn't find a free worker after 5 tries -- leave it
                -- pending rather than fail the whole script.
                v_final_status := 'pending';
            END IF;
        END IF;

        -- Payment (skip entirely for bookings still 'pending').
        IF v_final_status <> 'pending' THEN
            v_payment_roll := random();
            IF v_final_status = 'completed' THEN
                v_payment_status := CASE WHEN v_payment_roll < 0.90 THEN 'completed' ELSE 'refunded' END;
            ELSIF v_final_status = 'cancelled' THEN
                v_payment_status := CASE WHEN v_payment_roll < 0.60 THEN 'refunded' ELSE 'failed' END;
            ELSE
                v_payment_status := CASE WHEN v_payment_roll < 0.70 THEN 'pending' ELSE 'completed' END;
            END IF;
            v_payment_method := (ARRAY['cash','card','upi','wallet']::payment_method[])[1 + floor(random() * 4)::int];

            INSERT INTO payments(booking_id, amount, method, status, paid_at)
            VALUES (
                v_booking_id, v_total_amount, v_payment_method, v_payment_status,
                CASE WHEN v_payment_status = 'completed' THEN v_end + interval '2 hours' ELSE NULL END
            )
            ON CONFLICT DO NOTHING;
        END IF;

        -- Review: only for completed bookings, ~70% of the time.
        IF v_final_status = 'completed' AND random() < 0.70 THEN
            SELECT worker_id INTO v_worker_id FROM bookings WHERE booking_id = v_booking_id;
            IF v_worker_id IS NOT NULL THEN
                v_rating_roll := random();
                v_rating := CASE
                    WHEN v_rating_roll < 0.40 THEN 5
                    WHEN v_rating_roll < 0.75 THEN 4
                    WHEN v_rating_roll < 0.90 THEN 3
                    WHEN v_rating_roll < 0.97 THEN 2
                    ELSE 1
                END;
                INSERT INTO reviews(booking_id, customer_id, worker_id, rating, comment)
                VALUES (
                    v_booking_id, v_customer_id, v_worker_id, v_rating,
                    v_comments[1 + floor(random() * array_length(v_comments, 1))::int]
                )
                ON CONFLICT DO NOTHING;
            END IF;
        END IF;

        IF v_i % 200 = 0 THEN RAISE NOTICE 'Step 4: % / % bookings', v_i, v_target_bookings; END IF;
    END LOOP;
    RAISE NOTICE 'Step 4 done: % bookings processed', v_target_bookings;
END $$;

-- ----------------------------------------------------------------------------
-- Step 5: refresh the materialized view so the new bookings/reviews show
-- up in worker stats immediately.
-- ----------------------------------------------------------------------------

REFRESH MATERIALIZED VIEW mv_worker_stats;

-- ----------------------------------------------------------------------------
-- Summary counts
-- ----------------------------------------------------------------------------

SELECT 'users_total' AS metric, count(*)::text AS value FROM users
UNION ALL SELECT 'users_customers', count(*)::text FROM users WHERE role = 'customer'
UNION ALL SELECT 'users_workers', count(*)::text FROM users WHERE role = 'worker'
UNION ALL SELECT 'users_by_status_active', count(*)::text FROM users WHERE status = 'active'
UNION ALL SELECT 'users_by_status_inactive', count(*)::text FROM users WHERE status = 'inactive'
UNION ALL SELECT 'users_by_status_suspended', count(*)::text FROM users WHERE status = 'suspended'
UNION ALL SELECT 'skills', count(*)::text FROM skills
UNION ALL SELECT 'services', count(*)::text FROM services
UNION ALL SELECT 'workers', count(*)::text FROM workers
UNION ALL SELECT 'worker_availability', count(*)::text FROM worker_availability
UNION ALL SELECT 'worker_skills', count(*)::text FROM worker_skills
UNION ALL SELECT 'worker_services', count(*)::text FROM worker_services
UNION ALL SELECT 'bookings_total', count(*)::text FROM bookings
UNION ALL SELECT 'bookings_pending', count(*)::text FROM bookings WHERE status = 'pending'
UNION ALL SELECT 'bookings_confirmed', count(*)::text FROM bookings WHERE status = 'confirmed'
UNION ALL SELECT 'bookings_in_progress', count(*)::text FROM bookings WHERE status = 'in_progress'
UNION ALL SELECT 'bookings_completed', count(*)::text FROM bookings WHERE status = 'completed'
UNION ALL SELECT 'bookings_cancelled', count(*)::text FROM bookings WHERE status = 'cancelled'
UNION ALL SELECT 'booking_skills', count(*)::text FROM booking_skills
UNION ALL SELECT 'payments', count(*)::text FROM payments
UNION ALL SELECT 'reviews', count(*)::text FROM reviews
UNION ALL SELECT 'notifications', count(*)::text FROM notifications
UNION ALL SELECT 'notification_deliveries', count(*)::text FROM notification_deliveries
UNION ALL SELECT 'audit_log', count(*)::text FROM audit_log;
