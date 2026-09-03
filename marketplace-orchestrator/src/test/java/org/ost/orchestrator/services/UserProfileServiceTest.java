package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.model.Role;
import org.ost.platform.user.spi.UserAccountPort;
import org.ost.platform.user.spi.UserPort;
import org.ost.platform.user.spi.UserPreferencesPort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserProfileService#save} enforces service-boundary authorization: self-or-admin to edit
 * at all, and admin-editing-someone-else (never self, even an admin's own role) specifically to
 * change {@code role} -- the same single write path handles both name and role, so a missing
 * server-side role check would otherwise let the role field bypass the UI's read-only guard.
 * A self-edit reuses the one actor fetch instead of fetching the same row twice.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Long ACTOR_ID  = 10L;
    private static final Long TARGET_ID = 42L;

    @Mock private UserPort            userPort;
    @Mock private UserAccountPort     accountPort;
    @Mock private UserPreferencesPort preferencesPort;
    @Mock private AuthorizationService authorizationService;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userPort, accountPort, preferencesPort, authorizationService);
    }

    private static UserDto user(Long id, Role role) {
        return new UserDto(id, "Name", "email@example.com", role, null, null, 1L);
    }

    // --- self-edit (actingUserId == dto.id()) -- single actor fetch reused as the target ---

    @Test
    void save_selfEdit_noRoleChange_reusesActorFetchAndSkipsRoleCheck() {
        UserDto self = user(ACTOR_ID, Role.USER);
        UserProfileDto dto = new UserProfileDto(ACTOR_ID, "New Name", Role.USER, 1L);
        when(userPort.findById(ACTOR_ID)).thenReturn(Optional.of(self));

        service.save(dto, ACTOR_ID);

        verify(authorizationService).requireCanEditAccount(self, ACTOR_ID);
        verify(authorizationService, never()).requireCanEditRole(any(UserDto.class), any());
        verify(userPort).findById(ACTOR_ID);
        verify(accountPort).save(dto, ACTOR_ID);
    }

    @Test
    void save_selfEdit_roleChanged_invokesRequireCanEditRoleWithoutSecondFetch() {
        UserDto self = user(ACTOR_ID, Role.USER);
        UserProfileDto dto = new UserProfileDto(ACTOR_ID, "New Name", Role.ADMIN, 1L);
        when(userPort.findById(ACTOR_ID)).thenReturn(Optional.of(self));

        service.save(dto, ACTOR_ID);

        verify(authorizationService).requireCanEditRole(self, ACTOR_ID);
        verify(userPort).findById(ACTOR_ID);
        verify(accountPort).save(dto, ACTOR_ID);
    }

    @Test
    void save_selfEdit_roleChanged_deniedByRequireCanEditRole_throwsAndNeverSaves() {
        UserDto self = user(ACTOR_ID, Role.USER);
        UserProfileDto dto = new UserProfileDto(ACTOR_ID, "New Name", Role.ADMIN, 1L);
        when(userPort.findById(ACTOR_ID)).thenReturn(Optional.of(self));
        doThrow(new AccessDeniedException("denied")).when(authorizationService).requireCanEditRole(self, ACTOR_ID);

        assertThatThrownBy(() -> service.save(dto, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(accountPort, never()).save(any(), any());
    }

    // --- admin/moderator editing someone else -- two genuinely different rows ---

    @Test
    void save_editingOther_noRoleChange_fetchesBothActorAndTarget() {
        UserDto actor = user(ACTOR_ID, Role.ADMIN);
        UserDto target = user(TARGET_ID, Role.USER);
        UserProfileDto dto = new UserProfileDto(TARGET_ID, "New Name", Role.USER, 1L);
        when(userPort.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userPort.findById(TARGET_ID)).thenReturn(Optional.of(target));

        service.save(dto, ACTOR_ID);

        verify(authorizationService).requireCanEditAccount(actor, TARGET_ID);
        verify(authorizationService, never()).requireCanEditRole(any(UserDto.class), any());
        verify(accountPort).save(dto, ACTOR_ID);
    }

    @Test
    void save_editingOther_roleChanged_invokesRequireCanEditRoleWithActorDto() {
        UserDto actor = user(ACTOR_ID, Role.ADMIN);
        UserDto target = user(TARGET_ID, Role.USER);
        UserProfileDto dto = new UserProfileDto(TARGET_ID, "New Name", Role.MODERATOR, 1L);
        when(userPort.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userPort.findById(TARGET_ID)).thenReturn(Optional.of(target));

        service.save(dto, ACTOR_ID);

        verify(authorizationService).requireCanEditRole(actor, TARGET_ID);
        verify(accountPort).save(dto, ACTOR_ID);
    }

    @Test
    void save_editingOther_targetNotFound_skipsRoleCheckButStillSaves() {
        UserDto actor = user(ACTOR_ID, Role.ADMIN);
        UserProfileDto dto = new UserProfileDto(TARGET_ID, "Name", Role.ADMIN, 1L);
        when(userPort.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(userPort.findById(TARGET_ID)).thenReturn(Optional.empty());

        service.save(dto, ACTOR_ID);

        verify(authorizationService, never()).requireCanEditRole(any(UserDto.class), any());
        verify(accountPort).save(dto, ACTOR_ID);
    }

    @Test
    void save_deniedByRequireCanEditAccount_throwsAndNeverSaves() {
        UserDto actor = user(ACTOR_ID, Role.USER);
        UserProfileDto dto = new UserProfileDto(TARGET_ID, "New Name", Role.USER, 1L);
        when(userPort.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        doThrow(new AccessDeniedException("denied")).when(authorizationService).requireCanEditAccount(actor, TARGET_ID);

        assertThatThrownBy(() -> service.save(dto, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(accountPort, never()).save(any(), any());
        verify(userPort, never()).findById(TARGET_ID);
    }

    // --- actor row itself unresolvable -- fails closed via the id-based fallback ---

    @Test
    void save_actorNotFound_failsClosedAndNeverSaves() {
        UserProfileDto dto = new UserProfileDto(TARGET_ID, "New Name", Role.USER, 1L);
        when(userPort.findById(ACTOR_ID)).thenReturn(Optional.empty());
        doThrow(new AccessDeniedException("denied")).when(authorizationService).requireCanEditAccount(ACTOR_ID, TARGET_ID);

        assertThatThrownBy(() -> service.save(dto, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(accountPort, never()).save(any(), any());
    }
}
