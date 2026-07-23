package org.ost.marketplace.services.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.security.UserIdMarker;
import org.ost.platform.user.spi.UserPort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The app's only server-side authorization chokepoint — every overlay/view calls
 * {@link AccessEvaluator} instead of {@code @PreAuthorize} (intentionally absent, see
 * {@code marketplace-app/CLAUDE.md} "Security"). No Spring context needed: {@link UserPort} and
 * {@link AuthContextService} are mocked directly.
 */
@ExtendWith(MockitoExtension.class)
class AccessEvaluatorTest {

    private static final UserDto ADMIN_USER = user(1L, "admin@example.com");
    private static final UserDto MODERATOR_USER = user(2L, "moderator@example.com");
    private static final UserDto PLAIN_USER = user(3L, "plain@example.com");
    private static final UserIdMarker TARGET = () -> 99L;
    private static final Long TARGET_OWNER_ID = 99L;

    @Mock
    private UserPort userPort;

    @Mock
    private AuthContextService authContextService;

    private AccessEvaluator accessEvaluator;

    @BeforeEach
    void setUp() {
        accessEvaluator = new AccessEvaluator(userPort, authContextService);
    }

    private static UserDto user(Long id, String email) {
        return new UserDto(id, "Name", email, null, null, null, "en", 0L);
    }

    private void loggedOut() {
        when(authContextService.getCurrentUser()).thenReturn(Optional.empty());
    }

    private void loggedInAs(UserDto currentUser) {
        when(authContextService.getCurrentUser()).thenReturn(Optional.of(currentUser));
    }

    // --- isLoggedIn() ---

    @Test
    void isLoggedIn_true_whenUserPresent() {
        loggedInAs(PLAIN_USER);
        assertThat(accessEvaluator.isLoggedIn()).isTrue();
    }

    @Test
    void isLoggedIn_false_whenNoUser() {
        loggedOut();
        assertThat(accessEvaluator.isLoggedIn()).isFalse();
    }

    // --- isPrivileged() / canView() ---

    @Test
    void isPrivileged_true_forAdmin() {
        loggedInAs(ADMIN_USER);
        when(userPort.isAdmin(ADMIN_USER)).thenReturn(true);

        assertThat(accessEvaluator.isPrivileged()).isTrue();
        assertThat(accessEvaluator.canView()).isTrue();
    }

    @Test
    void isPrivileged_true_forModerator() {
        loggedInAs(MODERATOR_USER);
        when(userPort.isAdmin(MODERATOR_USER)).thenReturn(false);
        when(userPort.isModerator(MODERATOR_USER)).thenReturn(true);

        assertThat(accessEvaluator.isPrivileged()).isTrue();
        assertThat(accessEvaluator.canView()).isTrue();
    }

    @Test
    void isPrivileged_false_forPlainUser() {
        loggedInAs(PLAIN_USER);
        when(userPort.isAdmin(PLAIN_USER)).thenReturn(false);
        when(userPort.isModerator(PLAIN_USER)).thenReturn(false);

        assertThat(accessEvaluator.isPrivileged()).isFalse();
        assertThat(accessEvaluator.canView()).isFalse();
    }

    @Test
    void isPrivileged_false_whenLoggedOut() {
        loggedOut();
        assertThat(accessEvaluator.isPrivileged()).isFalse();
        assertThat(accessEvaluator.canView()).isFalse();
    }

    // --- getCurrentUserId() ---

    @Test
    void getCurrentUserId_returnsId_whenLoggedIn() {
        loggedInAs(PLAIN_USER);
        assertThat(accessEvaluator.getCurrentUserId()).isEqualTo(PLAIN_USER.id());
    }

    @Test
    void getCurrentUserId_returnsNull_whenLoggedOut() {
        loggedOut();
        assertThat(accessEvaluator.getCurrentUserId()).isNull();
    }

    // --- canOperate(UserIdMarker) / canNotEdit / canNotDelete ---

    @Test
    void canOperate_targetOverload_admin_returnsTrue_regardlessOfOwnership() {
        loggedInAs(ADMIN_USER);
        when(userPort.isAdmin(ADMIN_USER)).thenReturn(true);

        assertThat(accessEvaluator.canOperate(TARGET)).isTrue();
        assertThat(accessEvaluator.canNotEdit(TARGET)).isFalse();
        assertThat(accessEvaluator.canNotDelete(TARGET)).isFalse();
    }

    @Test
    void canOperate_targetOverload_moderator_returnsTrue_regardlessOfOwnership() {
        loggedInAs(MODERATOR_USER);
        when(userPort.isAdmin(MODERATOR_USER)).thenReturn(false);
        when(userPort.isModerator(MODERATOR_USER)).thenReturn(true);

        assertThat(accessEvaluator.canOperate(TARGET)).isTrue();
        assertThat(accessEvaluator.canNotEdit(TARGET)).isFalse();
        assertThat(accessEvaluator.canNotDelete(TARGET)).isFalse();
    }

    @Test
    void canOperate_targetOverload_owner_returnsTrue_whenNotPrivileged() {
        loggedInAs(PLAIN_USER);
        when(userPort.isAdmin(PLAIN_USER)).thenReturn(false);
        when(userPort.isModerator(PLAIN_USER)).thenReturn(false);
        when(userPort.isOwner(PLAIN_USER, TARGET)).thenReturn(true);

        assertThat(accessEvaluator.canOperate(TARGET)).isTrue();
        assertThat(accessEvaluator.canNotEdit(TARGET)).isFalse();
        assertThat(accessEvaluator.canNotDelete(TARGET)).isFalse();
    }

    @Test
    void canOperate_targetOverload_nonOwnerNonPrivileged_returnsFalse() {
        loggedInAs(PLAIN_USER);
        when(userPort.isAdmin(PLAIN_USER)).thenReturn(false);
        when(userPort.isModerator(PLAIN_USER)).thenReturn(false);
        when(userPort.isOwner(PLAIN_USER, TARGET)).thenReturn(false);

        assertThat(accessEvaluator.canOperate(TARGET)).isFalse();
        assertThat(accessEvaluator.canNotEdit(TARGET)).isTrue();
        assertThat(accessEvaluator.canNotDelete(TARGET)).isTrue();
    }

    @Test
    void canOperate_targetOverload_loggedOut_returnsFalse() {
        loggedOut();

        assertThat(accessEvaluator.canOperate(TARGET)).isFalse();
        assertThat(accessEvaluator.canNotEdit(TARGET)).isTrue();
        assertThat(accessEvaluator.canNotDelete(TARGET)).isTrue();
    }

    // --- canOperate(Long ownerUserId) / canNotEdit / canNotDelete ---

    @Test
    void canOperate_longOverload_admin_returnsTrue_regardlessOfOwnership() {
        loggedInAs(ADMIN_USER);
        when(userPort.isAdmin(ADMIN_USER)).thenReturn(true);

        assertThat(accessEvaluator.canOperate(TARGET_OWNER_ID)).isTrue();
        assertThat(accessEvaluator.canNotEdit(TARGET_OWNER_ID)).isFalse();
        assertThat(accessEvaluator.canNotDelete(TARGET_OWNER_ID)).isFalse();
    }

    @Test
    void canOperate_longOverload_owner_returnsTrue_whenNotPrivileged() {
        loggedInAs(PLAIN_USER);
        when(userPort.isAdmin(PLAIN_USER)).thenReturn(false);
        when(userPort.isModerator(PLAIN_USER)).thenReturn(false);
        when(userPort.isOwner(PLAIN_USER, TARGET_OWNER_ID)).thenReturn(true);

        assertThat(accessEvaluator.canOperate(TARGET_OWNER_ID)).isTrue();
        assertThat(accessEvaluator.canNotEdit(TARGET_OWNER_ID)).isFalse();
        assertThat(accessEvaluator.canNotDelete(TARGET_OWNER_ID)).isFalse();
    }

    @Test
    void canOperate_longOverload_nonOwnerNonPrivileged_returnsFalse() {
        loggedInAs(PLAIN_USER);
        when(userPort.isAdmin(PLAIN_USER)).thenReturn(false);
        when(userPort.isModerator(PLAIN_USER)).thenReturn(false);
        when(userPort.isOwner(PLAIN_USER, TARGET_OWNER_ID)).thenReturn(false);

        assertThat(accessEvaluator.canOperate(TARGET_OWNER_ID)).isFalse();
        assertThat(accessEvaluator.canNotEdit(TARGET_OWNER_ID)).isTrue();
        assertThat(accessEvaluator.canNotDelete(TARGET_OWNER_ID)).isTrue();
    }

    @Test
    void canOperate_longOverload_loggedOut_returnsFalse() {
        loggedOut();

        assertThat(accessEvaluator.canOperate(TARGET_OWNER_ID)).isFalse();
        assertThat(accessEvaluator.canNotEdit(TARGET_OWNER_ID)).isTrue();
        assertThat(accessEvaluator.canNotDelete(TARGET_OWNER_ID)).isTrue();
    }
}
