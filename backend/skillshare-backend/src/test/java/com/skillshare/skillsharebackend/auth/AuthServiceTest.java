package com.skillshare.skillsharebackend.auth;

import com.skillshare.skillsharebackend.domain.User;
import com.skillshare.skillsharebackend.domain.Worker;
import com.skillshare.skillsharebackend.domain.enums.AccountStatus;
import com.skillshare.skillsharebackend.domain.enums.UserRole;
import com.skillshare.skillsharebackend.repository.UserRepository;
import com.skillshare.skillsharebackend.repository.WorkerRepository;
import com.skillshare.skillsharebackend.web.dto.AuthResponse;
import com.skillshare.skillsharebackend.web.dto.ChangePasswordRequest;
import com.skillshare.skillsharebackend.web.dto.LoginRequest;
import com.skillshare.skillsharebackend.web.dto.RegisterRequest;
import com.skillshare.skillsharebackend.web.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService} - registration validation/duplicate
 * guards, the worker-role side-effect (bare {@code workers} row), and
 * login's credential checks. {@link JwtService} is mocked here (its own
 * generate/parse round-trip is covered by {@link JwtServiceTest}), and
 * {@link PasswordEncoder} is mocked rather than a real BCrypt instance,
 * so these tests exercise {@code AuthService}'s own branching only.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private UserRepository userRepository;
    private WorkerRepository workerRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        workerRepository = Mockito.mock(WorkerRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        service = new AuthService(userRepository, workerRepository, passwordEncoder, jwtService);
    }

    private RegisterRequest validCustomerRequest() {
        return new RegisterRequest(UserRole.customer, "Asha Patil", "asha@example.com", "9999999999", "password123");
    }

    private User savedUser(Long id, UserRole role, String email) {
        return savedUser(id, role, email, AccountStatus.active);
    }

    private User savedUser(Long id, UserRole role, String email, AccountStatus status) {
        return User.builder()
                .userId(id)
                .role(role)
                .fullName("Asha Patil")
                .email(email)
                .passwordHash("hashed")
                .status(status)
                .build();
    }

    @Test
    void register_succeeds_forCustomer_andDoesNotCreateWorkerRow() {
        RegisterRequest request = validCustomerRequest();
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");
        User saved = savedUser(1L, UserRole.customer, request.email());
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtService.generateToken(saved)).thenReturn("jwt-token");

        AuthResponse response = service.register(request);

        assertEquals("jwt-token", response.token());
        assertEquals(1L, response.userId());
        assertEquals(UserRole.customer, response.role());
        verify(workerRepository, never()).save(any());
    }

    @Test
    void register_forWorkerRole_alsoCreatesBareWorkerRow() {
        RegisterRequest request = new RegisterRequest(
                UserRole.worker, "Ravi Pawar", "ravi@example.com", "8888888888", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");
        User saved = savedUser(2L, UserRole.worker, request.email());
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtService.generateToken(saved)).thenReturn("jwt-token");

        service.register(request);

        ArgumentCaptor<Worker> workerCaptor = ArgumentCaptor.forClass(Worker.class);
        verify(workerRepository, times(1)).save(workerCaptor.capture());
        Worker createdWorker = workerCaptor.getValue();
        assertEquals(saved, createdWorker.getUser());
        assertEquals((short) 0, createdWorker.getExperienceYears());
        assertEquals(AccountStatus.active, createdWorker.getStatus());
    }

    @Test
    void register_rejectsDuplicateEmail() {
        RegisterRequest request = validCustomerRequest();
        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(savedUser(1L, UserRole.customer, request.email())));

        assertThrows(DuplicateEmailException.class, () -> service.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsMissingRole() {
        RegisterRequest request = new RegisterRequest(null, "Asha", "asha@example.com", null, "password123");
        assertThrows(AuthValidationException.class, () -> service.register(request));
    }

    @Test
    void register_rejectsBlankFullName() {
        RegisterRequest request = new RegisterRequest(UserRole.customer, "  ", "asha@example.com", null, "password123");
        assertThrows(AuthValidationException.class, () -> service.register(request));
    }

    @Test
    void register_rejectsInvalidEmail() {
        RegisterRequest request = new RegisterRequest(UserRole.customer, "Asha", "not-an-email", null, "password123");
        assertThrows(AuthValidationException.class, () -> service.register(request));
    }

    @Test
    void register_rejectsShortPassword() {
        RegisterRequest request = new RegisterRequest(UserRole.customer, "Asha", "asha@example.com", null, "short");
        assertThrows(AuthValidationException.class, () -> service.register(request));
    }

    @Test
    void login_succeeds_withCorrectPassword() {
        LoginRequest request = new LoginRequest("asha@example.com", "password123");
        User user = savedUser(1L, UserRole.customer, request.email());
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = service.login(request);

        assertEquals("jwt-token", response.token());
        assertEquals(1L, response.userId());
    }

    @Test
    void login_rejectsUnknownEmail() {
        LoginRequest request = new LoginRequest("nobody@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> service.login(request));
    }

    @Test
    void login_rejectsWrongPassword() {
        LoginRequest request = new LoginRequest("asha@example.com", "wrongpassword");
        User user = savedUser(1L, UserRole.customer, request.email());
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> service.login(request));
    }

    @Test
    void login_rejectsSuspendedAccount() {
        LoginRequest request = new LoginRequest("asha@example.com", "password123");
        User user = savedUser(1L, UserRole.customer, request.email(), AccountStatus.suspended);
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);

        assertThrows(AccountStatusException.class, () -> service.login(request));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_rejectsDeactivatedAccount() {
        LoginRequest request = new LoginRequest("asha@example.com", "password123");
        User user = savedUser(1L, UserRole.customer, request.email(), AccountStatus.inactive);
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);

        assertThrows(AccountStatusException.class, () -> service.login(request));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void deactivateSelf_succeeds_forActiveCustomer() {
        User user = savedUser(1L, UserRole.customer, "asha@example.com", AccountStatus.active);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.deactivateSelf(1L);

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        assertEquals(AccountStatus.inactive, savedCaptor.getValue().getStatus());
    }

    @Test
    void deactivateSelf_rejectsAdmin() {
        User admin = savedUser(1L, UserRole.admin, "admin@example.com", AccountStatus.active);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(AuthValidationException.class, () -> service.deactivateSelf(1L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateSelf_rejectsAlreadySuspended() {
        User user = savedUser(1L, UserRole.worker, "ravi@example.com", AccountStatus.suspended);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(AuthValidationException.class, () -> service.deactivateSelf(1L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateSelf_rejectsAlreadyInactive() {
        User user = savedUser(1L, UserRole.customer, "asha@example.com", AccountStatus.inactive);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(AuthValidationException.class, () -> service.deactivateSelf(1L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateSelf_rejectsUnknownUserId() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.deactivateSelf(99L));
    }

    @Test
    void deactivateSelf_syncsWorkerProfileStatus_whenCallerIsWorker() {
        User workerUser = savedUser(3L, UserRole.worker, "ravi@example.com", AccountStatus.active);
        Worker workerProfile = Worker.builder().workerId(5L).user(workerUser).status(AccountStatus.active).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(workerUser));
        when(workerRepository.findByUser_UserId(3L)).thenReturn(Optional.of(workerProfile));

        service.deactivateSelf(3L);

        // Mirrors AdminService.updateUserStatus's sync - this is what
        // keeps a self-deactivated worker from still being offered to
        // customers by the allocator (both paths filter on
        // workers.status, not users.status).
        assertEquals(AccountStatus.inactive, workerProfile.getStatus());
        verify(workerRepository).save(workerProfile);
    }

    // ---- updateProfile -----------------------------------------------

    @Test
    void updateProfile_updatesFullNameAndPhone_returnsFreshToken() {
        User user = savedUser(1L, UserRole.customer, "asha@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("new-jwt-token");

        AuthResponse response = service.updateProfile(1L, new UpdateProfileRequest("Asha P.", "9998887777"));

        assertEquals("Asha P.", user.getFullName());
        assertEquals("9998887777", user.getPhone());
        assertEquals("new-jwt-token", response.token());
        assertEquals("Asha P.", response.fullName());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_leavesFieldUntouched_whenNull() {
        User user = savedUser(1L, UserRole.customer, "asha@example.com");
        user.setPhone("9998887777");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        service.updateProfile(1L, new UpdateProfileRequest(null, null));

        assertEquals("Asha Patil", user.getFullName());
        assertEquals("9998887777", user.getPhone());
    }

    @Test
    void updateProfile_rejectsBlankFullName() {
        User user = savedUser(1L, UserRole.customer, "asha@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(AuthValidationException.class,
                () -> service.updateProfile(1L, new UpdateProfileRequest("  ", null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_rejectsUnknownUserId() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service.updateProfile(99L, new UpdateProfileRequest("New Name", null)));
    }

    // ---- changePassword ------------------------------------------------

    @Test
    void changePassword_succeeds_whenCurrentPasswordMatches() {
        User user = savedUser(1L, UserRole.customer, "asha@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass123", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("newpass456")).thenReturn("new-hashed");

        service.changePassword(1L, new ChangePasswordRequest("oldpass123", "newpass456"));

        assertEquals("new-hashed", user.getPasswordHash());
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_rejectsWrongCurrentPassword() {
        User user = savedUser(1L, UserRole.customer, "asha@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", user.getPasswordHash())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.changePassword(1L, new ChangePasswordRequest("wrongpass", "newpass456")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_rejectsShortNewPassword() {
        User user = savedUser(1L, UserRole.customer, "asha@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass123", user.getPasswordHash())).thenReturn(true);

        assertThrows(AuthValidationException.class,
                () -> service.changePassword(1L, new ChangePasswordRequest("oldpass123", "short")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_rejectsUnknownUserId() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service.changePassword(99L, new ChangePasswordRequest("oldpass123", "newpass456")));
    }
}
