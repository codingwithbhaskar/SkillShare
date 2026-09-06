-- ============================================================================
-- SkillShare — Schema v3
-- 05_functions_procedures_v3.sql
--
-- The architectural thesis of the whole project: allocation-critical logic
-- (candidate retrieval + scoring + assignment) lives in PL/pgSQL, not the
-- Java layer. Spring Boot calls into these via @Procedure/JdbcTemplate,
-- never reimplements the math.
--
-- Hardcoded design constants (not yet promoted into allocation_criteria):
--   - 30 km candidate search radius
--   - 5-job workload cap (used to normalize the workload score)
--   - 10-year experience cap (used to normalize the experience score)
--
-- Depends on: 01_schema_v3.sql, 04_triggers_v3.sql (fn_create_notification).
-- Run before 06_seed_data_v3.sql (the seed script CALLs sp_allocate_worker).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- fn_find_candidates — hard filters only. Skill match is NOT a hard filter
-- (a worker missing one of several requested skills can still be scored),
-- everything below IS a hard filter: active status, offers the service,
-- available at the requested window, within 30km, not already booked over
-- an overlapping window.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_find_candidates(p_booking_id BIGINT)
RETURNS TABLE(worker_id BIGINT) AS $$
DECLARE
    v_service_id      BIGINT;
    v_booking_geom    geography;
    v_start           TIMESTAMPTZ;
    v_end             TIMESTAMPTZ;
    v_dow             SMALLINT;
    v_local_start     TIME;
    v_local_end       TIME;
BEGIN
    SELECT b.service_id, loc.geom, b.scheduled_start, b.scheduled_end
      INTO v_service_id, v_booking_geom, v_start, v_end
    FROM bookings b
    JOIN locations loc ON loc.location_id = b.location_id
    WHERE b.booking_id = p_booking_id;

    -- Single-timezone assumption (India/regional marketplace): compare
    -- worker_availability's naive local TIME columns against the booking
    -- window converted to Asia/Kolkata, not UTC. Comparing against UTC
    -- silently shifts every comparison by 5.5 hours and makes every worker
    -- look unavailable.
    v_dow         := EXTRACT(DOW FROM (v_start AT TIME ZONE 'Asia/Kolkata'))::SMALLINT;
    v_local_start := (v_start AT TIME ZONE 'Asia/Kolkata')::TIME;
    v_local_end   := (v_end   AT TIME ZONE 'Asia/Kolkata')::TIME;

    RETURN QUERY
    SELECT w.worker_id
    FROM workers w
    JOIN worker_services wsvc ON wsvc.worker_id = w.worker_id AND wsvc.service_id = v_service_id
    JOIN locations wl ON wl.location_id = w.location_id
    WHERE w.status = 'active'
      AND ST_DWithin(wl.geom, v_booking_geom, 30000) -- 30 km radius
      AND EXISTS (
          SELECT 1 FROM worker_availability wa
          WHERE wa.worker_id = w.worker_id
            AND wa.day_of_week = v_dow
            AND wa.start_time <= v_local_start
            AND wa.end_time   >= v_local_end
      )
      AND NOT EXISTS (
          SELECT 1 FROM bookings ob
          WHERE ob.worker_id = w.worker_id
            AND ob.booking_id <> p_booking_id
            AND ob.status <> 'cancelled'
            AND ob.booking_range && tstzrange(v_start, v_end, '[)')
      );
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- fn_score_candidate — weighted sum of 6 normalized (0-1) factors. Weights
-- come from allocation_criteria (matched on the booking's urgency), with a
-- hardcoded fallback if no criteria row exists yet.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_score_candidate(p_booking_id BIGINT, p_worker_id BIGINT)
RETURNS TABLE(
    skill_score      NUMERIC,
    rating_score     NUMERIC,
    distance_score   NUMERIC,
    workload_score   NUMERIC,
    price_score      NUMERIC,
    experience_score NUMERIC,
    total_score      NUMERIC
) AS $$
DECLARE
    v_urgency          booking_urgency;
    v_service_id       BIGINT;
    v_booking_geom     geography;
    v_required_skills  INT;
    v_matched_skills   INT;
    v_skill_score      NUMERIC := 1.0;
    v_avg_rating       NUMERIC;
    v_rating_score     NUMERIC;
    v_distance_m       NUMERIC;
    v_distance_score   NUMERIC;
    v_active_bookings  INT;
    v_workload_score   NUMERIC;
    v_hourly_rate      NUMERIC;
    v_min_rate         NUMERIC;
    v_max_rate         NUMERIC;
    v_price_score      NUMERIC;
    v_experience_years INT;
    v_experience_score NUMERIC;
    v_skill_w NUMERIC; v_rating_w NUMERIC; v_distance_w NUMERIC;
    v_workload_w NUMERIC; v_price_w NUMERIC; v_experience_w NUMERIC;
BEGIN
    SELECT b.urgency, b.service_id, loc.geom
      INTO v_urgency, v_service_id, v_booking_geom
    FROM bookings b
    JOIN locations loc ON loc.location_id = b.location_id
    WHERE b.booking_id = p_booking_id;

    SELECT skill_weight, rating_weight, distance_weight, workload_weight, price_weight, experience_weight
      INTO v_skill_w, v_rating_w, v_distance_w, v_workload_w, v_price_w, v_experience_w
    FROM allocation_criteria
    WHERE criteria_type = v_urgency::text::criteria_type;

    IF NOT FOUND THEN
        -- Hardcoded fallback, mirrors the urgent/normal weight shift
        -- (workload weighted higher & price lower for urgent bookings).
        IF v_urgency = 'urgent' THEN
            v_skill_w := 0.30; v_rating_w := 0.15; v_distance_w := 0.15;
            v_workload_w := 0.25; v_price_w := 0.05; v_experience_w := 0.10;
        ELSE
            v_skill_w := 0.30; v_rating_w := 0.20; v_distance_w := 0.20;
            v_workload_w := 0.10; v_price_w := 0.15; v_experience_w := 0.05;
        END IF;
    END IF;

    -- Skill match: SOFT scoring factor, not a hard filter. A worker missing
    -- one of several requested skills can still be scored and potentially win.
    SELECT COUNT(*) INTO v_required_skills FROM booking_skills WHERE booking_id = p_booking_id;
    IF v_required_skills = 0 THEN
        v_skill_score := 1.0;
    ELSE
        SELECT COUNT(*) INTO v_matched_skills
        FROM booking_skills bs
        JOIN worker_skills wsk ON wsk.skill_id = bs.skill_id AND wsk.worker_id = p_worker_id
        WHERE bs.booking_id = p_booking_id;
        v_skill_score := v_matched_skills::NUMERIC / v_required_skills;
    END IF;

    -- Rating: cold-start default of 0.6 (a bit above neutral) for a worker
    -- with no reviews yet, so new workers aren't shut out entirely.
    SELECT avg_rating INTO v_avg_rating FROM mv_worker_stats WHERE worker_id = p_worker_id;
    IF v_avg_rating IS NULL OR v_avg_rating = 0 THEN
        v_rating_score := 0.6;
    ELSE
        v_rating_score := LEAST(v_avg_rating / 5.0, 1.0);
    END IF;

    -- Distance: closer is better, linearly scaled against the 30km search radius.
    SELECT ST_Distance(wl.geom, v_booking_geom) INTO v_distance_m
    FROM workers w JOIN locations wl ON wl.location_id = w.location_id
    WHERE w.worker_id = p_worker_id;
    v_distance_score := GREATEST(0, 1 - (v_distance_m / 30000.0));

    -- Workload: fewer active (confirmed/in_progress) bookings is better,
    -- normalized against a 5-job workload cap.
    SELECT active_bookings INTO v_active_bookings FROM mv_worker_stats WHERE worker_id = p_worker_id;
    v_workload_score := GREATEST(0, 1 - (COALESCE(v_active_bookings, 0)::NUMERIC / 5.0));

    -- Price fit: normalized among the OTHER current candidates for this same
    -- booking's service (not a global min/max) — cheaper relative to this
    -- specific candidate pool scores higher.
    SELECT ws.hourly_rate INTO v_hourly_rate
    FROM worker_services ws WHERE ws.worker_id = p_worker_id AND ws.service_id = v_service_id;

    SELECT MIN(ws.hourly_rate), MAX(ws.hourly_rate)
      INTO v_min_rate, v_max_rate
    FROM worker_services ws
    WHERE ws.service_id = v_service_id
      AND ws.worker_id IN (SELECT c.worker_id FROM fn_find_candidates(p_booking_id) c);

    IF v_max_rate IS NULL OR v_max_rate = v_min_rate THEN
        v_price_score := 1.0;
    ELSE
        v_price_score := 1 - ((v_hourly_rate - v_min_rate) / (v_max_rate - v_min_rate));
    END IF;

    -- Experience: normalized against a 10-year cap.
    SELECT experience_years INTO v_experience_years FROM workers WHERE worker_id = p_worker_id;
    v_experience_score := LEAST(COALESCE(v_experience_years, 0)::NUMERIC / 10.0, 1.0);

    RETURN QUERY SELECT
        v_skill_score, v_rating_score, v_distance_score, v_workload_score,
        v_price_score, v_experience_score,
        (v_skill_score * v_skill_w) + (v_rating_score * v_rating_w)
        + (v_distance_score * v_distance_w) + (v_workload_score * v_workload_w)
        + (v_price_score * v_price_w) + (v_experience_score * v_experience_w);
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- sp_allocate_worker — row-locks the booking, scores and logs every
-- candidate, then walks candidates best-score-first, retrying the next
-- candidate on an exclusion_violation (another concurrent allocation won the
-- race for that worker/time-slot). Fires the worker-assigned notifications
-- on success.
--
-- Note: no explicit SAVEPOINT/ROLLBACK TO SAVEPOINT statements — a plain
-- BEGIN...EXCEPTION WHEN...END block already creates and manages an implicit
-- subtransaction; explicit SAVEPOINT commands are only valid in plain
-- SQL/psql sessions, not inside PL/pgSQL procedure bodies.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE PROCEDURE sp_allocate_worker(p_booking_id BIGINT)
LANGUAGE plpgsql AS $$
DECLARE
    v_candidate       RECORD;
    v_score           RECORD;
    v_assigned        BOOLEAN := false;
    v_customer_id     BIGINT;
    v_worker_user_id  BIGINT;
BEGIN
    -- Pessimistic lock: prevents two concurrent calls from both trying to
    -- allocate the same booking.
    PERFORM 1 FROM bookings WHERE booking_id = p_booking_id FOR UPDATE;

    FOR v_candidate IN
        SELECT c.worker_id FROM fn_find_candidates(p_booking_id) c
    LOOP
        SELECT * INTO v_score FROM fn_score_candidate(p_booking_id, v_candidate.worker_id);

        INSERT INTO allocation_log(
            booking_id, worker_id, skill_score, rating_score, distance_score,
            workload_score, price_score, experience_score, total_score, is_selected
        ) VALUES (
            p_booking_id, v_candidate.worker_id, v_score.skill_score, v_score.rating_score,
            v_score.distance_score, v_score.workload_score, v_score.price_score,
            v_score.experience_score, v_score.total_score, false
        );
    END LOOP;

    FOR v_candidate IN
        SELECT worker_id, total_score FROM allocation_log
        WHERE booking_id = p_booking_id
        ORDER BY total_score DESC
    LOOP
        BEGIN
            UPDATE bookings
               SET worker_id = v_candidate.worker_id, status = 'confirmed'
             WHERE booking_id = p_booking_id;

            UPDATE allocation_log SET is_selected = true
             WHERE booking_id = p_booking_id AND worker_id = v_candidate.worker_id;

            SELECT customer_id INTO v_customer_id FROM bookings WHERE booking_id = p_booking_id;
            SELECT user_id INTO v_worker_user_id FROM workers WHERE worker_id = v_candidate.worker_id;

            PERFORM fn_create_notification(
                v_customer_id, p_booking_id, 'WORKER_ASSIGNED', 'Worker assigned',
                'A worker has been assigned to your booking #' || p_booking_id || '.',
                ARRAY['email','sms']::notification_channel[]
            );
            PERFORM fn_create_notification(
                v_worker_user_id, p_booking_id, 'WORKER_ASSIGNED', 'New job assigned',
                'You have been assigned to booking #' || p_booking_id || '.',
                ARRAY['email','sms']::notification_channel[]
            );

            v_assigned := true;
            EXIT;
        EXCEPTION WHEN exclusion_violation THEN
            -- This candidate was taken by a concurrent allocation for an
            -- overlapping slot; move on to the next-best candidate.
            CONTINUE;
        END;
    END LOOP;

    IF NOT v_assigned THEN
        RAISE NOTICE 'sp_allocate_worker: no available worker could be confirmed for booking %', p_booking_id;
    END IF;
END;
$$;
