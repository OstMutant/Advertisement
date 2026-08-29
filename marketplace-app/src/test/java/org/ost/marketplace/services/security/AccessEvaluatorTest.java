package org.ost.marketplace.services.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.CurrentUserService;
import org.ost.platform.user.dto.UserDto;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The app's only server-side authorization chokepoint — every overlay/view calls
 * {@link AccessEvaluator} instead of {@code @PreAuthorize} (intentionally absent, see
 * {@code marketplace-app/CLAUDE.md} "Security"). No Spring context needed:
 * {@link AuthorizationService} and {@link CurrentUserService} are mocked directly.
 */
@ExtendWith(MockitoExtension.class)
class AccessEvaluatorTest {

    private static final UserDto ADMIN_USER = user(1L, "admin@example.com");
    private static final UserDto MODERATOR_USER = user(2L, "moderator@example.com");
    private static final UserDto PLAIN_USER = user(3L, "plain@example.com");
    private static final Long TARGET_OWNER_ID = 99L;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private CurrentUserService currentUserService;

    private AccessEvaluator accessEvaluator;

    @BeforeEach
    void setUp() {
        accessEvaluator = new AccessEvaluator(authorizationService, currentUserService);
    }

    private static UserDto user(Long id, String email) {
        return new UserDto(id, "Name", email, null, null, null, 0L);
    }

    private void loggedOut() {
        when(currentUserService.getCurrentUser()).thenReturn(Optional.empty());
    }

    private void loggedInAs(UserDto currentUser) {
        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
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
        when(authorizationService.isAdmin(ADMIN_USER)).thenReturn(true);

        assertThat(accessEvaluator.isPrivileged()).isTrue();
        assertThat(accessEvaluator.canView()).isTrue();
    }

    @Test
    void isPrivileged_true_forModerator() {
        loggedInAs(MODERATOR_USER);
        when(authorizationService.isAdmin(MODERATOR_USER)).thenReturn(false);
        when(authorizationService.isModerator(MODERATOR_USER)).thenReturn(true);

        assertThat(accessEvaluator.isPrivileged()).isTrue();
        assertThat(accessEvaluator.canView()).isTrue();
    }

    @Test
    void isPrivileged_false_forPlainUser() {
        loggedInAs(PLAIN_USER);
        when(authorizationService.isAdmin(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isModerator(PLAIN_USER)).thenReturn(false);

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

    // --- canOperate(Long ownerUserId) / canNotEdit / canNotDelete ---

    @Test
    void canOperate_longOverload_admin_returnsTrue_regardlessOfOwnership() {
        loggedInAs(ADMIN_USER);
        when(authorizationService.isAdmin(ADMIN_USER)).thenReturn(true);

        assertThat(accessEvaluator.canOperate(TARGET_OWNER_ID)).isTrue();
        assertThat(accessEvaluator.canNotEdit(TARGET_OWNER_ID)).isFalse();
        assertThat(accessEvaluator.canNotDelete(TARGET_OWNER_ID)).isFalse();
    }

    @Test
    void canOperate_longOverload_owner_returnsTrue_whenNotPrivileged() {
        loggedInAs(PLAIN_USER);
        when(authorizationService.isAdmin(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isModerator(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isOwner(PLAIN_USER, TARGET_OWNER_ID)).thenReturn(true);

        assertThat(accessEvaluator.canOperate(TARGET_OWNER_ID)).isTrue();
        assertThat(accessEvaluator.canNotEdit(TARGET_OWNER_ID)).isFalse();
        assertThat(accessEvaluator.canNotDelete(TARGET_OWNER_ID)).isFalse();
    }

    @Test
    void canOperate_longOverload_nonOwnerNonPrivileged_returnsFalse() {
        loggedInAs(PLAIN_USER);
        when(authorizationService.isAdmin(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isModerator(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isOwner(PLAIN_USER, TARGET_OWNER_ID)).thenReturn(false);

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

    // --- canViewUserAccount(Long targetUserId) ---

    @Test
    void canViewUserAccount_admin_true_viewingOther() {
        loggedInAs(ADMIN_USER);
        when(authorizationService.isAdmin(ADMIN_USER)).thenReturn(true);

        assertThat(accessEvaluator.canViewUserAccount(TARGET_OWNER_ID)).isTrue();
    }

    @Test
    void canViewUserAccount_moderator_true_viewingOther() {
        loggedInAs(MODERATOR_USER);
        when(authorizationService.isAdmin(MODERATOR_USER)).thenReturn(false);
        when(authorizationService.isModerator(MODERATOR_USER)).thenReturn(true);

        assertThat(accessEvaluator.canViewUserAccount(TARGET_OWNER_ID)).isTrue();
    }

    @Test
    void canViewUserAccount_plainUser_true_viewingSelf() {
        loggedInAs(PLAIN_USER);
        when(authorizationService.isAdmin(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isModerator(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isOwner(PLAIN_USER, TARGET_OWNER_ID)).thenReturn(true);

        assertThat(accessEvaluator.canViewUserAccount(TARGET_OWNER_ID)).isTrue();
    }

    @Test
    void canViewUserAccount_plainUser_false_viewingOther() {
        loggedInAs(PLAIN_USER);
        when(authorizationService.isAdmin(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isModerator(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isOwner(PLAIN_USER, TARGET_OWNER_ID)).thenReturn(false);

        assertThat(accessEvaluator.canViewUserAccount(TARGET_OWNER_ID)).isFalse();
    }

    @Test
    void canViewUserAccount_loggedOut_returnsFalse() {
        loggedOut();

        assertThat(accessEvaluator.canViewUserAccount(TARGET_OWNER_ID)).isFalse();
    }

    // --- canEditUserAccount(Long targetUserId) -- moderator is read-only, unlike canOperate() ---

    @Test
    void canEditUserAccount_admin_true_editingOther() {
        loggedInAs(ADMIN_USER);
        when(authorizationService.isAdmin(ADMIN_USER)).thenReturn(true);

        assertThat(accessEvaluator.canEditUserAccount(TARGET_OWNER_ID)).isTrue();
    }

    @Test
    void canEditUserAccount_moderator_false_editingOther() {
        loggedInAs(MODERATOR_USER);
        when(authorizationService.isAdmin(MODERATOR_USER)).thenReturn(false);
        when(authorizationService.isOwner(MODERATOR_USER, TARGET_OWNER_ID)).thenReturn(false);

        assertThat(accessEvaluator.canEditUserAccount(TARGET_OWNER_ID)).isFalse();
    }

    @Test
    void canEditUserAccount_plainUser_true_editingSelf() {
        loggedInAs(PLAIN_USER);
        when(authorizationService.isAdmin(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isOwner(PLAIN_USER, TARGET_OWNER_ID)).thenReturn(true);

        assertThat(accessEvaluator.canEditUserAccount(TARGET_OWNER_ID)).isTrue();
    }

    @Test
    void canEditUserAccount_plainUser_false_editingOther() {
        loggedInAs(PLAIN_USER);
        when(authorizationService.isAdmin(PLAIN_USER)).thenReturn(false);
        when(authorizationService.isOwner(PLAIN_USER, TARGET_OWNER_ID)).thenReturn(false);

        assertThat(accessEvaluator.canEditUserAccount(TARGET_OWNER_ID)).isFalse();
    }

    @Test
    void canEditUserAccount_loggedOut_returnsFalse() {
        loggedOut();

        assertThat(accessEvaluator.canEditUserAccount(TARGET_OWNER_ID)).isFalse();
    }

    // --- canEditRole(Long targetUserId) ---

    @Test
    void canEditRole_admin_true_editingOther() {
        loggedInAs(ADMIN_USER);
        when(authorizationService.isAdmin(ADMIN_USER)).thenReturn(true);
        when(authorizationService.isOwner(ADMIN_USER, TARGET_OWNER_ID)).thenReturn(false);

        assertThat(accessEvaluator.canEditRole(TARGET_OWNER_ID)).isTrue();
    }

    @Test
    void canEditRole_admin_false_editingSelf() {
        loggedInAs(ADMIN_USER);
        when(authorizationService.isAdmin(ADMIN_USER)).thenReturn(true);
        when(authorizationService.isOwner(ADMIN_USER, ADMIN_USER.id())).thenReturn(true);

        assertThat(accessEvaluator.canEditRole(ADMIN_USER.id())).isFalse();
    }

    @Test
    void canEditRole_moderator_false_editingOther() {
        loggedInAs(MODERATOR_USER);
        when(authorizationService.isAdmin(MODERATOR_USER)).thenReturn(false);

        assertThat(accessEvaluator.canEditRole(TARGET_OWNER_ID)).isFalse();
    }

    @Test
    void canEditRole_plainUser_false_editingSelf() {
        loggedInAs(PLAIN_USER);
        when(authorizationService.isAdmin(PLAIN_USER)).thenReturn(false);

        assertThat(accessEvaluator.canEditRole(PLAIN_USER.id())).isFalse();
    }

    @Test
    void canEditRole_loggedOut_returnsFalse() {
        loggedOut();

        assertThat(accessEvaluator.canEditRole(TARGET_OWNER_ID)).isFalse();
    }
}
