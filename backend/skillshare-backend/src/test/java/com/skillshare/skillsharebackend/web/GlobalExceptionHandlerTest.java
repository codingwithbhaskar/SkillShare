package com.skillshare.skillsharebackend.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}'s validation-error
 * mappings - added after a live bug (confirmed 2026-09: {@code GET
 * /api/spatial/workers/nearby?lat=200&lon=73.85} returned a bare 500)
 * showed these three exception types had no mapping at all here, so they
 * fell through to Spring's own default handling - an opaque 500 instead
 * of a caller-actionable 400 explaining what was actually wrong with the
 * request.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unchecked")
    @Test
    void handleConstraintViolation_returns400_withViolationMessage() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("nearbyWorkers.lat");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be less than or equal to 90");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<String> response = handler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("nearbyWorkers.lat"));
        assertTrue(response.getBody().contains("must be less than or equal to 90"));
    }

    @Test
    void handleConstraintViolation_returnsGenericMessage_whenNoViolations() {
        ConstraintViolationException ex = new ConstraintViolationException(Set.of());

        ResponseEntity<String> response = handler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request parameters", response.getBody());
    }

    @Test
    void handleTypeMismatch_returns400() {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", BigDecimal.class, "lat", null, null);

        ResponseEntity<String> response = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("lat"));
    }

    @Test
    void handleMissingParameter_returns400() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("lat", "BigDecimal");

        ResponseEntity<String> response = handler.handleMissingParameter(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
