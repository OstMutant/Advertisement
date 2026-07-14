package org.ost.integrationtests.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.user.entity.User;
import org.ost.user.repository.UserRepository;
import org.ost.user.services.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers improvement-045 item 3: the registration-attempt rate-limiting counter in
 * {@link UserService#register}, keyed on {@code clientIp} alone (5 attempts / 15 min, counting
 * only {@link DuplicateKeyException} failures — see {@code user-spring-boot-starter/CLAUDE.md}).
 * Scoped to the threshold/key/reset logic that is actually ours to get wrong, not to Caffeine's
 * own time-based expiry (see {@code AuthServiceTest} in {@code marketplace-app} for the same
 * scoping rationale, and for the equivalent coverage of {@code AuthService.login()}'s counter —
 * keyed on {@code remoteAddr|email} and reset on success, unlike this one).
 *
 * <p>No Spring context, no Testcontainers — {@link UserRepository}/{@link PasswordEncoder}/
 * {@link ComponentFactory} are mocked directly. Lives in {@code integration-tests} because
 * {@link UserService} belongs to {@code user-spring-boot-starter}, a domain starter that never
 * carries its own test code (see {@code integration-tests/CLAUDE.md}).</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String CLIENT_IP = "203.0.113.1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ComponentFactory<AuditPort> auditPortFactory;

    private UserService userService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<AuditPort> auditPortProvider = mock(ObjectProvider.class);
        auditPortFactory = new ComponentFactory<>(auditPortProvider);
        userService = new UserService(userRepository, passwordEncoder, auditPortFactory);
        when(userRepository.countByFilter(UserFilterDto.empty())).thenReturn(5L);
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("encoded");
    }

    private static SignUpDto signUpDto(String email) {
        SignUpDto dto = new SignUpDto();
        dto.setName("Test User");
        dto.setEmail(email);
        dto.setPassword("password123");
        return dto;
    }

    // doReturn/doThrow, not when(...).thenReturn/thenThrow — these two helpers re-stub the same
    // mock method back and forth within a single test; when(...) would invoke the mock to record
    // the stub, which re-triggers whatever throwing behavior is already configured, before the
    // new stub can be attached.
    private void stubSaveSucceeds() {
        doReturn(User.builder().id(1L).build()).when(userRepository).save(any());
    }

    private void stubSaveThrowsDuplicateKey() {
        doThrow(new DuplicateKeyException("email already exists")).when(userRepository).save(any());
    }

    @Test
    void register_success_savesUser() {
        stubSaveSucceeds();
        userService.register(signUpDto("new@example.com"), CLIENT_IP);
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void register_duplicateEmail_incrementsAttemptsAndPropagatesException() {
        stubSaveThrowsDuplicateKey();
        assertThatThrownBy(() -> userService.register(signUpDto("taken@example.com"), CLIENT_IP))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void register_thresholdReached_throwsIllegalStateException_beforeAttemptingSave() {
        stubSaveThrowsDuplicateKey();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> userService.register(signUpDto("taken@example.com"), CLIENT_IP))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        assertThatThrownBy(() -> userService.register(signUpDto("taken@example.com"), CLIENT_IP))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, times(5)).save(any());
    }

    @Test
    void register_successAfterDuplicateKeyFailures_doesNotResetAttempts() {
        stubSaveThrowsDuplicateKey();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> userService.register(signUpDto("taken@example.com"), CLIENT_IP))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        stubSaveSucceeds();
        userService.register(signUpDto("fresh@example.com"), CLIENT_IP);

        // Unlike AuthService.login(), a successful registration does NOT reset the IP's counter —
        // the 3 prior failures still count, so 2 more duplicate-key failures (total 5) must block
        // the very next attempt.
        stubSaveThrowsDuplicateKey();
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> userService.register(signUpDto("taken@example.com"), CLIENT_IP))
                    .isInstanceOf(DuplicateKeyException.class);
        }
        assertThatThrownBy(() -> userService.register(signUpDto("taken@example.com"), CLIENT_IP))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void register_differentIpsTrackedSeparately() {
        stubSaveThrowsDuplicateKey();
        String blockedIp = "203.0.113.9";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> userService.register(signUpDto("taken@example.com"), blockedIp))
                    .isInstanceOf(DuplicateKeyException.class);
        }
        assertThatThrownBy(() -> userService.register(signUpDto("taken@example.com"), blockedIp))
                .isInstanceOf(IllegalStateException.class);

        // A different IP must not be affected by blockedIp's attempts.
        stubSaveSucceeds();
        userService.register(signUpDto("new@example.com"), CLIENT_IP);
    }
}
