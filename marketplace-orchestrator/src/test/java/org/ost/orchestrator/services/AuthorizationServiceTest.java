package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.model.Role;
import org.ost.platform.user.spi.UserAuthorizationPort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Plain Mockito unit test for {@link AuthorizationService}'s service-boundary checks. */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    private static final Long OWNER_ID  = 5L;
    private static final Long ACTING_ID = 42L;

    @Mock private UserAuthorizationPort authorizationPort;
    @Mock private ActorLookupService    actorLookupService;

    private AuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationService(authorizationPort, actorLookupService);
    }

    private UserDto user(Role role) {
        return new UserDto(ACTING_ID, "actor", "actor@example.com", role, null, null, null);
    }

    private void stubActor(UserDto actor) {
        when(actorLookupService.findById(ACTING_ID)).thenReturn(Optional.of(actor));
    }

    private void stubRole(UserDto actor, boolean admin, boolean moderator) {
        lenient().when(authorizationPort.isAdmin(actor)).thenReturn(admin);
        lenient().when(authorizationPort.isModerator(actor)).thenReturn(moderator);
    }

    // --- canOperate(UserDto, ownerId) -- the rule composition itself, no actor lookup ---

    @Test
    void canOperateUserDto_owner_true() {
        UserDto actor = user(Role.USER);
        stubRole(actor, false, false);
        when(authorizationPort.isOwner(actor, OWNER_ID)).thenReturn(true);

        assertThat(service.canOperate(actor, OWNER_ID)).isTrue();
    }

    @Test
    void canOperateUserDto_nullOwnerId_reducesToPrivilegedOnly() {
        UserDto actor = user(Role.ADMIN);
        stubRole(actor, true, false);

        assertThat(service.canOperate(actor, null)).isTrue();
    }

    @Test
    void canOperateUserDto_strangerNotPrivileged_false() {
        UserDto actor = user(Role.USER);
        stubRole(actor, false, false);
        when(authorizationPort.isOwner(actor, OWNER_ID)).thenReturn(false);

        assertThat(service.canOperate(actor, OWNER_ID)).isFalse();
    }

    // --- canEditAccount(UserDto, targetUserId) ---

    @Test
    void canEditAccountUserDto_self_true() {
        UserDto actor = user(Role.USER);

        assertThat(service.canEditAccount(actor, ACTING_ID)).isTrue();
    }

    @Test
    void canEditAccountUserDto_moderator_false_editingOther() {
        UserDto actor = user(Role.MODERATOR);
        stubRole(actor, false, true);

        assertThat(service.canEditAccount(actor, OWNER_ID)).isFalse();
    }

    // --- canEditRole(UserDto, targetUserId) ---

    @Test
    void canEditRoleUserDto_admin_true_editingOther() {
        UserDto actor = user(Role.ADMIN);
        stubRole(actor, true, false);

        assertThat(service.canEditRole(actor, OWNER_ID)).isTrue();
    }

    @Test
    void canEditRoleUserDto_admin_false_editingSelf() {
        UserDto actor = user(Role.ADMIN);
        stubRole(actor, true, false);

        assertThat(service.canEditRole(actor, ACTING_ID)).isFalse();
    }

    @Test
    void canEditRoleUserDto_moderator_false_editingOther() {
        UserDto actor = user(Role.MODERATOR);
        stubRole(actor, false, true);

        assertThat(service.canEditRole(actor, OWNER_ID)).isFalse();
    }

    // --- canEditRole(actingUserId, targetUserId) -- id-based, resolves then delegates ---

    @Test
    void canEditRole_admin_true_editingOther() {
        UserDto actor = user(Role.ADMIN);
        stubActor(actor);
        stubRole(actor, true, false);

        assertThat(service.canEditRole(ACTING_ID, OWNER_ID)).isTrue();
    }

    @Test
    void canEditRole_admin_false_editingSelf() {
        UserDto actor = user(Role.ADMIN);
        stubActor(actor);
        stubRole(actor, true, false);

        assertThat(service.canEditRole(ACTING_ID, ACTING_ID)).isFalse();
    }

    @Test
    void canEditRole_missingActor_false() {
        when(actorLookupService.findById(ACTING_ID)).thenReturn(Optional.empty());

        assertThat(service.canEditRole(ACTING_ID, OWNER_ID)).isFalse();
    }

    @Test
    void requireCanEditRole_denied_throwsAccessDeniedException() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);

        assertThatThrownBy(() -> service.requireCanEditRole(ACTING_ID, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireCanEditRole_allowed_doesNotThrow() {
        UserDto actor = user(Role.ADMIN);
        stubActor(actor);
        stubRole(actor, true, false);

        service.requireCanEditRole(ACTING_ID, OWNER_ID);
    }

    // --- isPrivileged(actingUserId) / requireIsPrivileged(actingUserId) -- alias for canOperate(id, null) ---

    @Test
    void isPrivileged_true_forAdmin() {
        UserDto actor = user(Role.ADMIN);
        stubActor(actor);
        stubRole(actor, true, false);

        assertThat(service.isPrivileged(ACTING_ID)).isTrue();
    }

    @Test
    void isPrivileged_false_forPlainUser() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);

        assertThat(service.isPrivileged(ACTING_ID)).isFalse();
    }

    @Test
    void requireIsPrivileged_denied_throwsAccessDeniedException() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);

        assertThatThrownBy(() -> service.requireIsPrivileged(ACTING_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireIsPrivileged_allowed_doesNotThrow() {
        UserDto actor = user(Role.MODERATOR);
        stubActor(actor);
        stubRole(actor, false, true);

        service.requireIsPrivileged(ACTING_ID);
    }

    // --- canOperate(actingUserId, ownerId) ---

    @Test
    void canOperate_owner_true() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);
        when(authorizationPort.isOwner(actor, OWNER_ID)).thenReturn(true);

        assertThat(service.canOperate(ACTING_ID, OWNER_ID)).isTrue();
    }

    @Test
    void canOperate_admin_true_evenNotOwner() {
        UserDto actor = user(Role.ADMIN);
        stubActor(actor);
        stubRole(actor, true, false);
        lenient().when(authorizationPort.isOwner(eq(actor), any())).thenReturn(false);

        assertThat(service.canOperate(ACTING_ID, OWNER_ID)).isTrue();
    }

    @Test
    void canOperate_moderator_true_evenNotOwner() {
        UserDto actor = user(Role.MODERATOR);
        stubActor(actor);
        stubRole(actor, false, true);
        lenient().when(authorizationPort.isOwner(eq(actor), any())).thenReturn(false);

        assertThat(service.canOperate(ACTING_ID, OWNER_ID)).isTrue();
    }

    @Test
    void canOperate_strangerNotPrivileged_false() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);
        when(authorizationPort.isOwner(actor, OWNER_ID)).thenReturn(false);

        assertThat(service.canOperate(ACTING_ID, OWNER_ID)).isFalse();
    }

    @Test
    void canOperate_nullOwnerId_reducesToPrivilegedOnly() {
        UserDto actor = user(Role.MODERATOR);
        stubActor(actor);
        stubRole(actor, false, true);

        assertThat(service.canOperate(ACTING_ID, null)).isTrue();
    }

    @Test
    void canOperate_nullOwnerId_plainUser_false() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);

        assertThat(service.canOperate(ACTING_ID, null)).isFalse();
    }

    @Test
    void canOperate_missingActor_false() {
        when(actorLookupService.findById(ACTING_ID)).thenReturn(Optional.empty());

        assertThat(service.canOperate(ACTING_ID, OWNER_ID)).isFalse();
    }

    // --- canEditAccount(actingUserId, targetUserId) -- stricter, no moderator ---

    @Test
    void canEditAccount_self_true() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);

        assertThat(service.canEditAccount(ACTING_ID, ACTING_ID)).isTrue();
    }

    @Test
    void canEditAccount_admin_true_editingOther() {
        UserDto actor = user(Role.ADMIN);
        stubActor(actor);
        stubRole(actor, true, false);

        assertThat(service.canEditAccount(ACTING_ID, OWNER_ID)).isTrue();
    }

    @Test
    void canEditAccount_moderator_false_editingOther() {
        UserDto actor = user(Role.MODERATOR);
        stubActor(actor);
        stubRole(actor, false, true);

        assertThat(service.canEditAccount(ACTING_ID, OWNER_ID)).isFalse();
    }

    @Test
    void canEditAccount_missingActor_false() {
        when(actorLookupService.findById(ACTING_ID)).thenReturn(Optional.empty());

        assertThat(service.canEditAccount(ACTING_ID, OWNER_ID)).isFalse();
    }

    // --- requireCanOperate / requireCanEditAccount -- throwing wrappers ---

    @Test
    void requireCanOperate_denied_throwsAccessDeniedException() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);
        when(authorizationPort.isOwner(actor, OWNER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.requireCanOperate(ACTING_ID, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireCanOperate_allowed_doesNotThrow() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);
        when(authorizationPort.isOwner(actor, OWNER_ID)).thenReturn(true);

        service.requireCanOperate(ACTING_ID, OWNER_ID);
    }

    @Test
    void requireCanEditAccount_denied_throwsAccessDeniedException() {
        UserDto actor = user(Role.MODERATOR);
        stubActor(actor);
        stubRole(actor, false, true);

        assertThatThrownBy(() -> service.requireCanEditAccount(ACTING_ID, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireCanEditAccount_allowed_doesNotThrow() {
        UserDto actor = user(Role.USER);
        stubActor(actor);
        stubRole(actor, false, false);

        service.requireCanEditAccount(ACTING_ID, ACTING_ID);
    }

    // --- requireCanEditAccount(UserDto, ...) / requireCanEditRole(UserDto, ...) -- no actor lookup ---

    @Test
    void requireCanEditAccountUserDto_denied_throwsAccessDeniedException() {
        UserDto actor = user(Role.MODERATOR);
        stubRole(actor, false, true);

        assertThatThrownBy(() -> service.requireCanEditAccount(actor, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireCanEditAccountUserDto_allowed_doesNotThrow() {
        UserDto actor = user(Role.USER);
        stubRole(actor, false, false);

        service.requireCanEditAccount(actor, ACTING_ID);
    }

    @Test
    void requireCanEditRoleUserDto_denied_throwsAccessDeniedException() {
        UserDto actor = user(Role.USER);
        stubRole(actor, false, false);

        assertThatThrownBy(() -> service.requireCanEditRole(actor, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireCanEditRoleUserDto_allowed_doesNotThrow() {
        UserDto actor = user(Role.ADMIN);
        stubRole(actor, true, false);

        service.requireCanEditRole(actor, OWNER_ID);
    }
}
