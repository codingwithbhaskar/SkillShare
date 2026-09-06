package com.skillshare.skillsharebackend.repository;

import com.skillshare.skillsharebackend.domain.Payment;
import com.skillshare.skillsharebackend.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBooking_BookingId(Long bookingId);

    /** Looked up by {@code PaymentService} when a webhook event arrives -
     *  the payload identifies the payment by Razorpay's own order id, not
     *  our booking id. */
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    /**
     * All-time revenue for {@code AdminStatsResponse} - every completed
     * payment, no date bound. {@code COALESCE} so an empty table sums to
     * zero rather than null (matters for a fresh/seed-only database).
     *
     * <p>{@code status} is bound as a parameter rather than written as an
     * inline JPQL enum literal ({@code p.status =
     * com.skillshare...PaymentStatus.completed}) - that pattern is used
     * elsewhere in this codebase (e.g. {@code BookingRepository
     * .findOverlapping}) but this Hibernate version renders an inline
     * literal comparison against a {@code @JdbcTypeCode(NAMED_ENUM)}
     * column as {@code '...'::PaymentStatus} - the Java class's simple
     * name, not the actual Postgres type name ({@code payment_status}) -
     * which fails with "type does not exist" at execution time (confirmed
     * live 2026-09-01). Binding the enum as a normal parameter goes
     * through the attribute's registered JdbcType correctly instead.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    BigDecimal sumByStatus(@Param("status") PaymentStatus status);

    /** Date-scoped revenue for {@code AdminReportResponse} - see that
     *  record's javadoc for why this is bound by {@code paidAt} rather
     *  than a booking's scheduled window, and {@link #sumByStatus}'s
     *  javadoc for why {@code status} is a bind parameter. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
            + "WHERE p.status = :status AND p.paidAt >= :from AND p.paidAt < :to")
    BigDecimal sumByStatusBetween(
            @Param("status") PaymentStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
