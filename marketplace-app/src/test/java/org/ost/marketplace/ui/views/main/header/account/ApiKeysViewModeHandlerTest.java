package org.ost.marketplace.ui.views.main.header.account;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.ApiKeyManagementService;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.apikey.model.ApiKeyAlreadyRevokedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.ost.marketplace.services.i18n.I18nKey.API_KEYS_VIEW_NOTIFICATION_ALREADY_REVOKED;
import static org.ost.marketplace.services.i18n.I18nKey.API_KEYS_VIEW_NOTIFICATION_LOAD_ERROR;
import static org.ost.marketplace.services.i18n.I18nKey.API_KEYS_VIEW_NOTIFICATION_NOT_FOUND;
import static org.ost.marketplace.services.i18n.I18nKey.API_KEYS_VIEW_NOTIFICATION_REVOKED;

/** The tab lists only the signed-in user's keys, and a failed revoke never breaks the tab. */
@ExtendWith(MockitoExtension.class)
class ApiKeysViewModeHandlerTest {

    private static final Long USER_ID = 7L;

    @Mock private ApiKeyManagementService apiKeyManagementService;
    @Mock private AccessEvaluator access;
    @Mock private NotificationService notificationService;
    @Mock(answer = Answers.RETURNS_SMART_NULLS) private I18nService i18nService;

    private ApiKeysViewModeHandler handler;

    @BeforeEach
    void setUp() {
        when(access.getCurrentUserId()).thenReturn(USER_ID);
        handler = new ApiKeysViewModeHandler(apiKeyManagementService, access, notificationService, i18nService);
        handler.configure(ApiKeysViewModeHandler.Parameters.builder()
                .onClose(() -> {})
                .tabBar(new Div())
                .build());
    }

    @Test
    void buildPrimaryContent_listsTheSignedInUsersActiveKeysOnly() {
        List<ApiKeySummaryDto> keys = List.of(key(1L), key(2L));
        when(apiKeyManagementService.listForActor(USER_ID, false)).thenReturn(keys);

        handler.buildPrimaryContent();

        verify(apiKeyManagementService).listForActor(USER_ID, false);
        assertEquals(keys, List.copyOf(grid().getListDataView().getItems().toList()));
        assertTrue(grid().isVisible());
        assertFalse(emptyText().isVisible());
    }

    @Test
    void showRevokedChecked_reloadsWithRevokedKeys() {
        when(apiKeyManagementService.listForActor(USER_ID, false)).thenReturn(List.of(key(1L)));
        when(apiKeyManagementService.listForActor(USER_ID, true)).thenReturn(List.of(key(1L), key(2L)));
        Div body = handler.buildPrimaryContent();

        showRevokedBox(body).setValue(true);

        verify(apiKeyManagementService).listForActor(USER_ID, true);
        assertEquals(2, grid().getListDataView().getItems().count());
    }

    @Test
    void noKeys_showsTheEmptyText() {
        when(apiKeyManagementService.listForActor(USER_ID, false)).thenReturn(List.of());

        handler.buildPrimaryContent();

        assertFalse(grid().isVisible());
        assertTrue(emptyText().isVisible());
    }

    @Test
    void loadFails_showsTheLoadErrorAndTheEmptyText() {
        when(apiKeyManagementService.listForActor(USER_ID, false)).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> handler.buildPrimaryContent());

        verify(notificationService).error(API_KEYS_VIEW_NOTIFICATION_LOAD_ERROR);
        assertTrue(emptyText().isVisible());
    }

    @Test
    void revoke_usesTheSignedInUsersIdAndReloads() {
        ApiKeySummaryDto key = key(5L);
        when(apiKeyManagementService.listForActor(USER_ID, false)).thenReturn(List.of(key));
        handler.buildPrimaryContent();

        handler.revoke(key);

        verify(apiKeyManagementService).revoke(USER_ID, key.id());
        verify(notificationService).success(API_KEYS_VIEW_NOTIFICATION_REVOKED);
        verify(apiKeyManagementService, times(2)).listForActor(USER_ID, false);
    }

    @Test
    void revoke_alreadyRevoked_reportsItAndReloads() {
        ApiKeySummaryDto key = key(5L);
        when(apiKeyManagementService.listForActor(USER_ID, false)).thenReturn(List.of(key));
        doThrow(new ApiKeyAlreadyRevokedException("already")).when(apiKeyManagementService).revoke(USER_ID, key.id());
        handler.buildPrimaryContent();

        assertDoesNotThrow(() -> handler.revoke(key));

        verify(notificationService).error(API_KEYS_VIEW_NOTIFICATION_ALREADY_REVOKED);
        verify(apiKeyManagementService, times(2)).listForActor(USER_ID, false);
    }

    @Test
    void revoke_notFound_reportsIt() {
        ApiKeySummaryDto key = key(5L);
        when(apiKeyManagementService.listForActor(USER_ID, false)).thenReturn(List.of(key));
        doThrow(new NoSuchElementException("missing")).when(apiKeyManagementService).revoke(USER_ID, key.id());
        handler.buildPrimaryContent();

        assertDoesNotThrow(() -> handler.revoke(key));

        verify(notificationService).error(API_KEYS_VIEW_NOTIFICATION_NOT_FOUND);
    }

    private static ApiKeySummaryDto key(Long id) {
        return new ApiKeySummaryDto(id, "mk_" + id, "label " + id, Instant.parse("2026-01-01T00:00:00Z"), null, null);
    }

    @SuppressWarnings("unchecked")
    private Grid<ApiKeySummaryDto> grid() {
        return (Grid<ApiKeySummaryDto>) ReflectionTestUtils.getField(handler, "grid");
    }

    private Span emptyText() {
        return (Span) ReflectionTestUtils.getField(handler, "emptyText");
    }

    private Checkbox showRevokedBox(Div body) {
        return body.getChildren()
                .flatMap(Component::getChildren)
                .filter(Checkbox.class::isInstance)
                .map(Checkbox.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
