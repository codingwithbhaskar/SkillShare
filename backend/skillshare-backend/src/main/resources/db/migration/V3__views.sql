-- ============================================================================
-- SkillShare — Schema v3
-- 03_views_v3.sql
--
-- Six views, each tied to a named use rather than generic coverage.
-- Depends on: 01_schema_v3.sql, 04_triggers_v3.sql (mv_worker_stats is
-- populated by REFRESH, not by these views, but vw_top_workers reads it).
-- ============================================================================

-- vw_worker_profile — search/browse: one row per worker, M:N skills/services
-- folded into arrays/jsonb so the frontend doesn't have to de-duplicate rows.
CREATE OR REPLACE VIEW vw_worker_profile AS
SELECT
    w.worker_id,
    u.full_name,
    u.email,
    u.phone,
    w.bio,
    w.experience_years,
    w.status,
    l.city,
    l.state,
    l.latitude,
    l.longitude,
    COALESCE(sk.skills, '{}')      AS skills,
    COALESCE(svc.services, '[]'::jsonb) AS services,
    COALESCE(ms.avg_rating, 0)     AS avg_rating,
    COALESCE(ms.review_count, 0)   AS review_count
FROM workers w
JOIN users u ON u.user_id = w.user_id
LEFT JOIN locations l ON l.location_id = w.location_id
LEFT JOIN (
    SELECT ws.worker_id, ARRAY_AGG(s.skill_name ORDER BY s.skill_name) AS skills
    FROM worker_skills ws JOIN skills s ON s.skill_id = ws.skill_id
    GROUP BY ws.worker_id
) sk ON sk.worker_id = w.worker_id
LEFT JOIN (
    SELECT wsvc.worker_id,
           jsonb_agg(jsonb_build_object(
               'service_id', sv.service_id,
               'service_name', sv.service_name,
               'hourly_rate', wsvc.hourly_rate
           ) ORDER BY sv.service_name) AS services
    FROM worker_services wsvc JOIN services sv ON sv.service_id = wsvc.service_id
    GROUP BY wsvc.worker_id
) svc ON svc.worker_id = w.worker_id
LEFT JOIN mv_worker_stats ms ON ms.worker_id = w.worker_id;

-- vw_booking_details — full picture: customer, worker, service, location,
-- required skills, payment, review.
CREATE OR REPLACE VIEW vw_booking_details AS
SELECT
    b.booking_id,
    b.status,
    b.urgency,
    b.scheduled_start,
    b.scheduled_end,
    b.total_amount,
    cu.user_id      AS customer_id,
    cu.full_name    AS customer_name,
    cu.phone        AS customer_phone,
    w.worker_id,
    wu.full_name    AS worker_name,
    wu.phone        AS worker_phone,
    sv.service_id,
    sv.service_name,
    loc.address_line,
    loc.city,
    loc.latitude,
    loc.longitude,
    COALESCE(rs.required_skills, '{}') AS required_skills,
    p.payment_id,
    p.amount        AS payment_amount,
    p.status        AS payment_status,
    r.review_id,
    r.rating        AS review_rating,
    r.comment       AS review_comment
FROM bookings b
JOIN users cu ON cu.user_id = b.customer_id
LEFT JOIN workers w ON w.worker_id = b.worker_id
LEFT JOIN users wu ON wu.user_id = w.user_id
JOIN services sv ON sv.service_id = b.service_id
JOIN locations loc ON loc.location_id = b.location_id
LEFT JOIN (
    SELECT bs.booking_id, ARRAY_AGG(s.skill_name ORDER BY s.skill_name) AS required_skills
    FROM booking_skills bs JOIN skills s ON s.skill_id = bs.skill_id
    GROUP BY bs.booking_id
) rs ON rs.booking_id = b.booking_id
LEFT JOIN payments p ON p.booking_id = b.booking_id
LEFT JOIN reviews r ON r.booking_id = b.booking_id;

-- vw_top_workers — admin reporting, backed by mv_worker_stats.
CREATE OR REPLACE VIEW vw_top_workers AS
SELECT
    w.worker_id,
    u.full_name,
    ms.completed_bookings,
    ms.avg_rating,
    ms.review_count
FROM mv_worker_stats ms
JOIN workers w ON w.worker_id = ms.worker_id
JOIN users u ON u.user_id = w.user_id
ORDER BY ms.completed_bookings DESC, ms.avg_rating DESC;

-- vw_top_services — admin reporting: booking volume and revenue per service.
CREATE OR REPLACE VIEW vw_top_services AS
SELECT
    sv.service_id,
    sv.service_name,
    COUNT(b.booking_id)                                        AS total_bookings,
    COUNT(b.booking_id) FILTER (WHERE b.status = 'completed')  AS completed_bookings,
    COALESCE(SUM(b.total_amount) FILTER (WHERE b.status = 'completed'), 0) AS total_revenue
FROM services sv
LEFT JOIN bookings b ON b.service_id = sv.service_id
GROUP BY sv.service_id, sv.service_name
ORDER BY total_bookings DESC;

-- vw_unread_notifications — in-app bell: same predicate as the partial
-- index idx_notifications_unread.
CREATE OR REPLACE VIEW vw_unread_notifications AS
SELECT notification_id, user_id, booking_id, type, title, message, created_at
FROM notifications
WHERE is_read = false
ORDER BY created_at DESC;

-- vw_pending_notification_deliveries — exactly what the Spring Boot outbox
-- dispatch job polls, pre-joined with recipient email/phone so it needs no
-- second query.
CREATE OR REPLACE VIEW vw_pending_notification_deliveries AS
SELECT
    nd.delivery_id,
    nd.notification_id,
    nd.channel,
    nd.attempt_count,
    n.type,
    n.title,
    n.message,
    u.user_id,
    u.email,
    u.phone
FROM notification_deliveries nd
JOIN notifications n ON n.notification_id = nd.notification_id
JOIN users u ON u.user_id = n.user_id
WHERE nd.status = 'pending'
ORDER BY nd.created_at;
