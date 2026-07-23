package org.ost.marketplace.ui.views.main.tabs.users.overlay.modes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.dto.UserEditDto;
import org.ost.marketplace.ui.mappers.UserMapper;
import org.ost.marketplace.ui.views.components.audit.AuditActivityPanel;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.model.Role;
import org.ost.platform.user.spi.UserPort;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for improvement-079: the activity panel must filter by the viewer's id
 * (the person looking at the profile), never by the profile subject's id.
 */
@ExtendWith(MockitoExtension.class)
class UserFormOverlayModeHandlerTest {

    @Mock private UserPort userPort;
    @Mock private UserMapper mapper;
    @Mock private AccessEvaluator access;
    @Mock private I18nService i18nService;
    @Mock private NotificationService notificationService;
    @Mock private UiComponentFactory<OverlayFormBinder<UserEditDto>> formBinderFactory;
    @Mock private ComponentFactory<AuditPort> auditPortFactory;
    @Mock private UiComponentFactory<AuditActivityPanel> auditActivityPanelFactory;

    private UserFormOverlayModeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserFormOverlayModeHandler(userPort, mapper, access, i18nService, notificationService,
                formBinderFactory, auditPortFactory, auditActivityPanelFactory);
    }

    @Test
    void buildActivityContentUsesViewerIdNotSubjectId() throws Exception {
        UserDto subject = new UserDto(99L, "Subject User", "subject@example.com", Role.USER,
                Instant.now(), Instant.now(), "en", 1L);
        when(access.getCurrentUserId()).thenReturn(42L);
        when(access.isPrivileged()).thenReturn(false);
        when(access.canOperate(99L)).thenReturn(true);

        handler.configure(UserFormOverlayModeHandler.Parameters.builder()
                .user(subject)
                .onSave(() -> {})
                .onCancel(() -> {})
                .build());

        Method buildActivityContent = UserFormOverlayModeHandler.class.getDeclaredMethod("buildActivityContent");
        buildActivityContent.setAccessible(true);
        buildActivityContent.invoke(handler);

        ArgumentCaptor<AuditActivityPanel.Parameters> captor = ArgumentCaptor.forClass(AuditActivityPanel.Parameters.class);
        verify(auditActivityPanelFactory).build(captor.capture());

        assertThat(captor.getValue().getUserId())
                .as("activity panel must filter by the viewer's id, not the profile subject's id")
                .isEqualTo(42L)
                .isNotEqualTo(subject.id());
    }
}
