package com.skillshare.skillsharebackend.web;

import com.skillshare.skillsharebackend.auth.AuthService;
import com.skillshare.skillsharebackend.auth.PasswordResetService;
import com.skillshare.skillsharebackend.web.dto.AuthResponse;
import com.skillshare.skillsharebackend.web.dto.ForgotPasswordRequest;
import com.skillshare.skillsharebackend.web.dto.LoginRequest;
import com.skillshare.skillsharebackend.web.dto.RegisterRequest;
import com.skillshare.skillsharebackend.web.dto.ResetPasswordRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 7 - authentication HTTP surface, wrapping {@link AuthService}.
 * All endpoints here are permitted without a token ({@code SecurityConfig}
 * {@code /api/auth/**} rule); everything else in the API requires a valid
 * bearer token.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Starts the "forgot password" flow. Always returns 202 regardless of
     * whether the email is registered - the response must never reveal
     * that. {@link PasswordResetService#requestReset} does the real work
     * (or nothing) behind that uniform response.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
    }

    /** Completes the flow: consumes the emailed token and sets the new
     *  password. 400 (via {@code GlobalExceptionHandler}) if the token is
     *  missing/unknown/used/expired or the new password is too short. */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }
}
