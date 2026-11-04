package org.ost.marketplace.ui.views.services.pagination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserSettingsDto;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A settings change reaches only the bars registered for that user, and never a bar that is detached from a UI. */
@ExtendWith(MockitoExtension.class)
class SettingsPaginationServiceTest {

    private static final long USER_ID = 42L;
    private static final long OTHER_USER_ID = 7L;

    @Mock private AuthContextService authContextService;
    @Mock private UserProfileService userProfileService;

    private SettingsPaginationService service;

    @Test
    void onSettingsChanged_withNoRegisteredBar_doesNothing() {
        service = new SettingsPaginationService(authContextService, userProfileService);

        service.onSettingsChanged(USER_ID, UserSettingsDto.defaultSettings());

        verifyNoInteractions(authContextService, userProfileService);
    }

    @Test
    void onSettingsChanged_forARegisteredBarWithoutAUi_doesNotRefreshOrThrow() {
        service = new SettingsPaginationService(authContextService, userProfileService);
        PaginationBar bar = mock(PaginationBar.class);
        Runnable refresh = mock(Runnable.class);
        when(authContextService.getCurrentUser()).thenReturn(Optional.of(user(USER_ID)));
        when(userProfileService.loadSettings(USER_ID)).thenReturn(UserSettingsDto.defaultSettings());
        when(bar.getUI()).thenReturn(Optional.empty());
        service.register(bar, UserSettingsDto::getAdsPageSize, refresh);
        clearInvocations(bar);

        service.onSettingsChanged(USER_ID, UserSettingsDto.defaultSettings());

        verify(bar, never()).setPageSize(anyInt());
        verify(refresh, never()).run();
    }

    @Test
    void onSettingsChanged_forAnotherUser_neverTouchesTheRegisteredBar() {
        service = new SettingsPaginationService(authContextService, userProfileService);
        PaginationBar bar = mock(PaginationBar.class);
        Runnable refresh = mock(Runnable.class);
        when(authContextService.getCurrentUser()).thenReturn(Optional.of(user(USER_ID)));
        when(userProfileService.loadSettings(USER_ID)).thenReturn(UserSettingsDto.defaultSettings());
        service.register(bar, UserSettingsDto::getAdsPageSize, refresh);
        clearInvocations(bar);

        service.onSettingsChanged(OTHER_USER_ID, UserSettingsDto.defaultSettings());

        verifyNoInteractions(bar, refresh);
    }

    private static UserDto user(long id) {
        return new UserDto(id, "Owner", "owner@example.com", null, null, null, 0L);
    }
}
