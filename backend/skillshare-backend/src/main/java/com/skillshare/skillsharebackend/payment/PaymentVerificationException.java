package com.skillshare.skillsharebackend.payment;

/** Thrown by {@link PaymentService} when a signature check fails -
 *  either the checkout callback's {@code razorpay_signature} (via
 *  {@code Utils.verifyPaymentSignature}) or the server webhook's
 *  {@code X-Razorpay-Signature} header (via
 *  {@code Utils.verifyWebhookSignature}) - or when Razorpay's own
 *  authoritative payment record (fetched server-side, never trusted from
 *  the client) doesn't show a captured payment. Mapped to HTTP 400 by
 *  {@code GlobalExceptionHandler} - this is a client-input problem, not
 *  an upstream Razorpay failure (see {@link PaymentGatewayException} for
 *  that case). */
public class PaymentVerificationException extends RuntimeException {
    public PaymentVerificationException(String message) {
        super(message);
    }
}
