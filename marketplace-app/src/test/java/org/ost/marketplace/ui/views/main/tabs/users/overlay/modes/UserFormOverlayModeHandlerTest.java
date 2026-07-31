package org.ost.marketplace.ui.views.main.tabs.users.overlay.modes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.dto.UserEditDto;
import org.ost.marketplace.ui.mappers.UserMapper;
import org.ost.marketplace.ui.views.components.audit.EntityActivityOverlay;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.model.Role;
import org.ost.platform.user.spi.UserAccountPort;
import org.ost.platform.user.spi.UserPort;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for improvement-079: the activity panel must filter by the viewer's id
 * (the person looking at the profile), never by the profile subject's id.
 */
@ExtendWith(MockitoExtension.class)
class UserFormOverlayModeHandlerTest {

    @Mock private UserPort userPort;
    @Mock private UserAccountPort accountPort;
    @Mock private UserMapper mapper;
    @Mock private AccessEvaluator access;
    @Mock private I18nService i18nService;
    @Mock private NotificationService notificationService;
    @Mock private UiComponentFactory<OverlayFormBinder<UserEditDto>> formBinderFactory;
    @Mock private ComponentFactory<AuditPort> auditPortFactory;
    @Mock private EntityActivityOverlay entityActivityOverlay;

    private UserFormOverlayModeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserFormOverlayModeHandler(userPort, accountPort, mapper, access, i18nService, notificationService,
                formBinderFactory, auditPortFactory, entityActivityOverlay);
    }

    @Test
    void buildActivityContentUsesViewerIdNotSubjectId() throws Exception {
        UserDto subject = new UserDto(99L, "Subject User", "subject@example.com", Role.USER,
                Instant.now(), Instant.now(), 1L);
        when(access.getCurrentUserId()).thenReturn(42L);
        when(access.isPrivileged()).thenReturn(false);
        when(access.canOperate(99L)).thenReturn(true);
        when(i18nService.get(any(I18nKey.class))).thenReturn("Activity");

        AtomicBoolean cancelCalled = new AtomicBoolean(false);
        AtomicBoolean closeToListCalled = new AtomicBoolean(false);
        List<BreadcrumbStep> breadcrumbSteps = List.of(new BreadcrumbStep("Users", () -> closeToListCalled.set(true)));

        handler.configure(UserFormOverlayModeHandler.Parameters.builder()
                .user(subject)
                .onSave(() -> {})
                .onCancel(() -> cancelCalled.set(true))
                .breadcrumbSteps(breadcrumbSteps)
                .build());

        Method buildHistoryButton = UserFormOverlayModeHandler.class.getDeclaredMethod("buildHistoryButton");
        buildHistoryButton.setAccessible(true);
        UiIconButton historyButton = (UiIconButton) buildHistoryButton.invoke(handler);
        historyButton.click();

        ArgumentCaptor<EntityActivityOverlay.Parameters> captor = ArgumentCaptor.forClass(EntityActivityOverlay.Parameters.class);
        verify(entityActivityOverlay).openFor(captor.capture());

        assertThat(captor.getValue().getUserId())
                .as("activity panel must filter by the viewer's id, not the profile subject's id")
                .isEqualTo(42L)
                .isNotEqualTo(subject.id());

        assertThat(captor.getValue().getParentSteps())
                .as("the breadcrumb chain passed into the nested overlay must be the exact one built by UserOverlay, not rebuilt from onCancel")
                .isEqualTo(breadcrumbSteps);
        assertThat(captor.getValue().getParentFormLabel())
                .as("the immediate-parent link must show the user's name, matching UserOverlay's own current-page label")
                .isEqualTo(subject.name());

        captor.getValue().getParentSteps().get(0).onClick().run();
        assertThat(closeToListCalled.get())
                .as("the list step's own action must close all the way to the list, not just cancel the edit")
                .isTrue();
        assertThat(cancelCalled.get())
                .as("the list step must not go through onCancel, which reverts to View for a session entered from View")
                .isFalse();
    }
}
