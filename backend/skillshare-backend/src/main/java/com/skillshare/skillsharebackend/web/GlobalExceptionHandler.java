package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.allocation.BookingNotAllocatableException;
import com.skillshare.skillsharebackend.admin.AdminNotFoundException;
import com.skillshare.skillsharebackend.admin.AdminValidationException;
import com.skillshare.skillsharebackend.allocation.BookingNotFoundException;
import com.skillshare.skillsharebackend.allocation.WorkerNotFoundException;
import com.skillshare.skillsharebackend.auth.AccountStatusException;
import com.skillshare.skillsharebackend.auth.AuthValidationException;
import com.skillshare.skillsharebackend.auth.DuplicateEmailException;
import com.skillshare.skillsharebackend.auth.InvalidCredentialsException;
import com.skillshare.skillsharebackend.auth.InvalidResetTokenException;
import com.skillshare.skillsharebackend.auth.UserNotFoundException;
import com.skillshare.skillsharebackend.booking.BookingValidationException;
import com.skillshare.skillsharebackend.booking.InvalidBookingTransitionException;
import com.skillshare.skillsharebackend.booking.ReviewNotAllowedException;
import com.skillshare.skillsharebackend.dashboard.WorkerProfileValidationException;
import com.skillshare.skillsharebackend.payment.PaymentGatewayException;
import com.skillshare.skillsharebackend.payment.PaymentNotAllowedException;
import com.skillshare.skillsharebackend.payment.PaymentValidationException;
import com.skillshare.skillsharebackend.payment.PaymentVerificationException;
import com.skillshare.skillsharebackend.security.ForbiddenException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralizes exception -&gt; HTTP status mapping for the whole REST
 * surface. Through Phase 4 this only covered the allocation-related
 * exceptions; Phase 5 added booking-lifecycle and review-submission
 * exceptions from the {@code booking} package; Phase 6 adds the
 * {@code payment} package's - all in the same
 * {@code @RestControllerAdvice} rather than one per phase.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<String> handleBookingNotFound(BookingNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(WorkerNotFoundException.class)
    public ResponseEntity<String> handleWorkerNotFound(WorkerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(BookingNotAllocatableException.class)
    public ResponseEntity<String> handleBookingNotAllocatable(BookingNotAllocatableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(BookingValidationException.class)
    public ResponseEntity<String> handleBookingValidation(BookingValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidBookingTransitionException.class)
    public ResponseEntity<String> handleInvalidBookingTransition(InvalidBookingTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(ReviewNotAllowedException.class)
    public ResponseEntity<String> handleReviewNotAllowed(ReviewNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(PaymentValidationException.class)
    public ResponseEntity<String> handlePaymentValidation(PaymentValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(PaymentNotAllowedException.class)
    public ResponseEntity<String> handlePaymentNotAllowed(PaymentNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(PaymentVerificationException.class)
    public ResponseEntity<String> handlePaymentVerification(PaymentVerificationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<String> handlePaymentGateway(PaymentGatewayException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
    }

    @ExceptionHandler(AuthValidationException.class)
    public ResponseEntity<String> handleAuthValidation(AuthValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<String> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<String> handleInvalidResetToken(InvalidResetTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<String> handleAccountStatus(AccountStatusException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    /** Authenticated, but not entitled to this specific resource - see
     *  {@link ForbiddenException}'s javadoc for how this differs from
     *  Spring Security's own role-based 403. */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<String> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler(WorkerProfileValidationException.class)
    public ResponseEntity<String> handleWorkerProfileValidation(WorkerProfileValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(AdminNotFoundException.class)
    public ResponseEntity<String> handleAdminNotFound(AdminNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(AdminValidationException.class)
    public ResponseEntity<String> handleAdminValidation(AdminValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    /** Defense-in-depth fallback behind {@code AdminService}'s explicit
     *  reference checks on service/skill deletion (e.g. a booking or
     *  worker-service row created concurrently, in the gap between the
     *  check and the DELETE) - turns a raw FK-violation 500 into a
     *  meaningful 409 instead. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("This action conflicts with existing data and cannot be completed.");
    }
}
