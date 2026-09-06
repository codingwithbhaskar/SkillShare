package com.skillshare.skillsharebackend.domain;

import com.skillshare.skillsharebackend.domain.enums.PaymentMethod;
import com.skillshare.skillsharebackend.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Maps to `payments` — 1:1 with bookings. Phase 6 resolved the payment
 *  scope decision in favor of a real gateway (Razorpay, test/sandbox
 *  mode) — see PaymentService. The three gateway* columns were added in
 *  the V6 migration and are all nullable: a row exists (status
 *  `pending`) before any of them are known, and they fill in as
 *  PaymentService walks a booking through order creation, checkout, and
 *  signature verification. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "method")
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    /** Razorpay's id for the order created before checkout (`order_...`). */
    @Column(name = "gateway_order_id", length = 64)
    private String gatewayOrderId;

    /** Razorpay's id for the actual payment, set once the checkout
     *  callback (or the webhook) confirms it (`pay_...`). */
    @Column(name = "gateway_payment_id", length = 64)
    private String gatewayPaymentId;

    /** The HMAC signature Razorpay returned for this payment - kept for
     *  audit trail even though it's verified (not trusted from storage)
     *  at the moment it's received. */
    @Column(name = "gateway_signature", length = 128)
    private String gatewaySignature;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
