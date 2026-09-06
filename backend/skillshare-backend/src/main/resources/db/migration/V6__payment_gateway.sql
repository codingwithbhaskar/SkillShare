-- ============================================================================
-- V6 - Payment gateway integration (Phase 6).
--
-- Two changes, both additive (no existing column/row is touched):
--
-- 1. payments gains three nullable columns to hold Razorpay's own ids and
--    verification signature. A payment row exists (status 'pending')
--    before any of these are known - they fill in as PaymentService walks
--    a booking through order -> checkout -> webhook confirmation.
--
-- 2. sp_allocate_worker (defined in V4) is replaced to also compute
--    bookings.total_amount at the moment a worker is confirmed, using
--    that worker's own rate for the booking's service
--    (worker_services.hourly_rate) times the booking's duration in hours.
--    total_amount was never set anywhere before this - Phase 5's booking
--    creation always leaves it NULL, and nothing computed it later either
--    - which blocked Phase 6 from having anything to charge. It has to be
--    computed here, not earlier, because it depends on which worker
--    actually wins the allocation.
--
-- Canonical source: db/01_schema_v3.sql (payments table) and
-- db/05_functions_procedures_v3.sql (sp_allocate_worker) both carry this
-- change already, per db/README.md's process - edit the canonical file
-- first, then copy the delta into a new V<n+1> migration rather than
-- editing an already-applied V<n> file in place.
-- ============================================================================

ALTER TABLE payments
    ADD COLUMN gateway_order_id   VARCHAR(64),
    ADD COLUMN gateway_payment_id VARCHAR(64),
    ADD COLUMN gateway_signature  VARCHAR(128);

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
            -- total_amount = the assigned worker's rate for this specific
            -- service (worker_services.hourly_rate) times the booking's
            -- duration in hours - computed here, at confirmation time, not
            -- earlier: it depends on which worker actually wins, so it
            -- can't be known before this point, and it must be known
            -- before Phase 6 payment can create a gateway order for this
            -- booking's amount.
            UPDATE bookings
               SET worker_id = v_candidate.worker_id,
                   status = 'confirmed',
                   total_amount = (
                       SELECT wsvc.hourly_rate
                              * (EXTRACT(EPOCH FROM (bk.scheduled_end - bk.scheduled_start)) / 3600.0)
                       FROM bookings bk
                       JOIN worker_services wsvc
                         ON wsvc.worker_id = v_candidate.worker_id
                        AND wsvc.service_id = bk.service_id
                       WHERE bk.booking_id = p_booking_id
                   )
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
