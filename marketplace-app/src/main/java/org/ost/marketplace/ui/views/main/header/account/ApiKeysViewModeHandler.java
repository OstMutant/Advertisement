package org.ost.marketplace.ui.views.main.header.account;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.query.utils.TimeZoneUtil;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.action.DeleteActionButton;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.ApiKeyManagementService;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.apikey.model.ApiKeyAlreadyRevokedException;
import org.springframework.context.annotation.Scope;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** {@code AccountOverlay}'s API keys section: lists and revokes the signed-in user's own keys, never showing a key value. */
@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ApiKeysViewModeHandler extends AbstractViewOverlayModeHandler
        implements Configurable<ApiKeysViewModeHandler, ApiKeysViewModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull Runnable onClose;
        @NonNull Component tabBar;
    }

    private final ApiKeyManagementService apiKeyManagementService;
    private final AccessEvaluator         access;
    private final NotificationService     notificationService;
    @Getter
    private final I18nService             i18nService;

    private Parameters params;
    private boolean showRevoked;
    private Grid<ApiKeySummaryDto> grid;
    private Span emptyText;

    @Override
    public ApiKeysViewModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    protected Div buildPrimaryContent() {
        Div cardHeader = new Div(VaadinIcon.KEY.create(), new Span(getValue(ACCOUNT_OVERLAY_TAB_API_KEYS)));
        cardHeader.addClassName("overlay__view-card-header");

        Span hint = new Span(getValue(API_KEYS_VIEW_HINT));
        hint.addClassName("api-keys-hint");

        Checkbox showRevokedBox = new Checkbox(getValue(API_KEYS_VIEW_SHOW_REVOKED));
        showRevokedBox.addClassName("api-keys-show-revoked");
        showRevokedBox.addValueChangeListener(e -> {
            showRevoked = Boolean.TRUE.equals(e.getValue());
            refresh();
        });

        grid = buildGrid();

        emptyText = new Span(getValue(API_KEYS_VIEW_EMPTY_TEXT));
        emptyText.addClassName("api-keys-empty-text");

        Div card = new Div(cardHeader, hint, showRevokedBox, grid, emptyText);
        card.addClassName("overlay__view-card");

        Div body = new Div(params.getTabBar(), card);
        body.addClassName("overlay__view-body");
        refresh();
        return body;
    }

    private Grid<ApiKeySummaryDto> buildGrid() {
        Grid<ApiKeySummaryDto> keyGrid = new Grid<>();
        keyGrid.addClassName("api-keys-grid");
        Comparator<Instant> nullsFirst = Comparator.nullsFirst(Comparator.naturalOrder());

        keyGrid.addColumn(this::displayLabel)
                .setHeader(getValue(API_KEYS_VIEW_HEADER_LABEL))
                .setFlexGrow(1);
        keyGrid.addColumn(new ComponentRenderer<>(key -> {
                    Span prefix = new Span(key.keyPrefix() + "...");
                    prefix.addClassName("api-key-prefix");
                    return prefix;
                }))
                .setHeader(getValue(API_KEYS_VIEW_HEADER_KEY))
                .setAutoWidth(true);
        keyGrid.addColumn(key -> TimeZoneUtil.formatInstantHuman(key.createdAt()))
                .setHeader(getValue(API_KEYS_VIEW_HEADER_CREATED))
                .setComparator(Comparator.comparing(ApiKeySummaryDto::createdAt, nullsFirst));
        keyGrid.addColumn(key -> TimeZoneUtil.formatInstantHuman(key.lastUsedAt(), getValue(API_KEYS_VIEW_NEVER_USED)))
                .setHeader(getValue(API_KEYS_VIEW_HEADER_LAST_USED))
                .setComparator(Comparator.comparing(ApiKeySummaryDto::lastUsedAt, nullsFirst));
        keyGrid.addColumn(key -> key.revokedAt() == null
                        ? getValue(API_KEYS_VIEW_STATUS_ACTIVE)
                        : getValue(API_KEYS_VIEW_STATUS_REVOKED, TimeZoneUtil.formatInstantHuman(key.revokedAt())))
                .setHeader(getValue(API_KEYS_VIEW_HEADER_STATUS));
        keyGrid.addColumn(new ComponentRenderer<>(key -> key.revokedAt() == null
                        ? new DeleteActionButton(getValue(API_KEYS_VIEW_BUTTON_REVOKE, displayLabel(key)),
                                () -> confirmAndRevoke(key), "api-key-revoke-button", false)
                        : new Span()))
                .setHeader(getValue(API_KEYS_VIEW_HEADER_ACTIONS));
        return keyGrid;
    }

    private String displayLabel(ApiKeySummaryDto key) {
        return key.label() != null ? key.label() : getValue(API_KEYS_VIEW_UNNAMED);
    }

    private void refresh() {
        try {
            List<ApiKeySummaryDto> keys = apiKeyManagementService.listForActor(access.getCurrentUserId(), showRevoked);
            grid.setItems(keys);
            grid.setVisible(!keys.isEmpty());
            emptyText.setVisible(keys.isEmpty());
        } catch (Exception ex) {
            log.error("Failed to load API keys", ex);
            notificationService.error(API_KEYS_VIEW_NOTIFICATION_LOAD_ERROR);
            grid.setItems(List.of());
            grid.setVisible(false);
            emptyText.setVisible(true);
        }
    }

    private void confirmAndRevoke(ApiKeySummaryDto key) {
        new ConfirmActionDialog(
                getValue(API_KEYS_VIEW_CONFIRM_REVOKE_TITLE),
                getValue(API_KEYS_VIEW_CONFIRM_REVOKE_TEXT, displayLabel(key), key.keyPrefix()),
                getValue(API_KEYS_VIEW_CONFIRM_REVOKE_BUTTON),
                getValue(API_KEYS_VIEW_CONFIRM_CANCEL_BUTTON),
                () -> revoke(key)
        ).open();
    }

    void revoke(ApiKeySummaryDto key) {
        try {
            apiKeyManagementService.revoke(access.getCurrentUserId(), key.id());
            notificationService.success(API_KEYS_VIEW_NOTIFICATION_REVOKED);
        } catch (ApiKeyAlreadyRevokedException _) {
            notificationService.error(API_KEYS_VIEW_NOTIFICATION_ALREADY_REVOKED);
        } catch (NoSuchElementException _) {
            notificationService.error(API_KEYS_VIEW_NOTIFICATION_NOT_FOUND);
        } catch (Exception ex) {
            log.error("Failed to revoke API key {}", key.id(), ex);
            notificationService.error(API_KEYS_VIEW_NOTIFICATION_REVOKE_ERROR);
        }
        refresh();
    }

    @Override
    protected Div buildHeaderActions() {
        UiIconButton closeButton = new UiIconButton(getValue(HEADER_HOME), VaadinIcon.CLOSE.create());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        return new Div(closeButton);
    }
}
