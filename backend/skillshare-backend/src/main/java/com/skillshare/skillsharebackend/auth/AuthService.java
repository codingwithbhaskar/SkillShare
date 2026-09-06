package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import com.skillshare.skillsharebackend.repository.UserRepository;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.web.dto.AuthResponse;
import com.skillshare.skillsharebackend.web.dto.LoginRequest;
import com.skillshare.skillsharebackend.web.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 7 - registration and login. Password hashing via Spring
 * Security's {@link PasswordEncoder} (BCrypt, see
 * {@code SecurityConfig.passwordEncoder()}); token issuance via
 * {@link JwtService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Creates the {@code users} row (every role) and, for role = worker,
     * also creates the required 1:1 {@code workers} row - the schema's
     * {@code workers.user_id NOT NULL UNIQUE} FK means a worker-role user
     * without one would fail every worker-dashboard/allocation code path
     * that joins through {@code workers}. The new worker row is
     * deliberately bare (no location, no hourly rate, no skills, 0 years
     * experience) - completing a worker's profile (location, rate,
     * skills, availability) isn't built yet; see dev-status-and-next-
     * steps.md's Phase 7 notes for that follow-up gap.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateRegistration(request);
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException("An account with email " + request.email() + " already exists");
        }

        User user = User.builder()
                .role(request.role())
                .fullName(request.fullName())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(AccountStatus.active)
                .build();
        user = userRepository.save(user);

        if (user.getRole() == UserRole.worker) {
            Worker worker = Worker.builder()
                    .user(user)
                    .experienceYears((short) 0)
                    .status(AccountStatus.active)
                    .build();
            workerRepository.save(worker);
        }

        log.info("AuthService: registered new {} user {} ({})", user.getRole(), user.getUserId(), user.getEmail());
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        // Checked after the password match (not before) so a wrong password
        // against a suspended/deactivated account still reports the generic
        // "Invalid email or password" - only a genuinely correct login
        // attempt reveals the account's status.
        if (user.getStatus() == AccountStatus.suspended) {
            throw new AccountStatusException("This account has been suspended. Contact support for help.");
        }
        if (user.getStatus() == AccountStatus.inactive) {
            throw new AccountStatusException("This account has been deactivated.");
        }
        log.info("AuthService: user {} logged in", user.getUserId());
        return toAuthResponse(user);
    }

    /**
     * Self-service account deactivation - closes the previously-dead
     * {@link AccountStatus#inactive} value (nothing anywhere in the app
     * ever set it before this). Blocked for admin accounts (an admin
     * locking themselves out has no recovery path - there's no "manage
     * other admins" endpoint) and for accounts an admin already suspended
     * (that state should only be reversed by an admin, via the existing
     * {@code /api/admin/users/{id}/status} reactivate action, not
     * silently swapped for a different non-active status by the user
     * themselves). Sets the account inactive; blocks future logins via
     * the check above. Like admin-triggered suspension, this does NOT
     * revoke any JWT already issued - this app has no token-revocation
     * mechanism, so an existing token stays valid until its natural
     * expiry either way.
     */
    @Transactional
    public void deactivateSelf(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No account found for id " + userId));
        if (user.getRole() == UserRole.admin) {
            throw new AuthValidationException(
                    "Admin accounts cannot self-deactivate. Ask another admin to manage this account.");
        }
        if (user.getStatus() == AccountStatus.suspended) {
            throw new AuthValidationException(
                    "This account is suspended by an administrator. Contact support instead of deactivating it yourself.");
        }
        if (user.getStatus() == AccountStatus.inactive) {
            throw new AuthValidationException("This account is already deactivated.");
        }
        user.setStatus(AccountStatus.inactive);
        userRepository.save(user);
        // Same workers.status sync AdminService.updateUserStatus does for
        // an admin-triggered suspend/reactivate - see that method's
        // comment for why this column specifically is what every
        // allocation path filters on.
        if (user.getRole() == UserRole.worker) {
            workerRepository.findByUser_UserId(userId).ifPresent(worker -> {
                worker.setStatus(AccountStatus.inactive);
                workerRepository.save(worker);
            });
        }
        log.info("AuthService: user {} deactivated their own account", userId);
    }

    private void validateRegistration(RegisterRequest request) {
        if (request.role() == null) {
            throw new AuthValidationException("role is required (admin, customer, or worker)");
        }
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new AuthValidationException("fullName is required");
        }
        if (request.email() == null || !request.email().contains("@")) {
            throw new AuthValidationException("A valid email is required");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new AuthValidationException("password must be at least 8 characters");
        }
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUserId(), user.getRole(), user.getFullName(), user.getEmail());
    }
}
