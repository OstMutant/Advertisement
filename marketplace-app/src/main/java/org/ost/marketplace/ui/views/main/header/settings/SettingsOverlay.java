package org.ost.marketplace.ui.views.main.header.settings;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.core.UiComponentFactory;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class SettingsOverlay extends AbstractEntityOverlay<SettingsFormModeHandler> {

    @Getter private final transient EntityOverlaySupport support;
    private final transient AuthContextService           authContextService;
    private final transient UiComponentFactory<SettingsFormModeHandler> formHandlerFactory;

    private Long currentUserId;

    @Override protected String  getOverlayCssClass()   { return "settings-overlay"; }
    @Override protected I18nKey getBreadcrumbLabelKey() { return HEADER_HOME; }

    @Override
    protected SaveConfig saveConfig() {
        return new SaveConfig(SETTINGS_SAVED_SUCCESS, null, null);
    }

    @Override
    protected void proceed() {
        // settings overlay stays open after save — nothing to close or notify
    }

    @Override
    protected void afterDiscard() {
        closeToList();
    }

    public void openSettings() {
        authContextService.getCurrentUser().ifPresent(user -> {
            currentUserId = user.id();
            ensureInitialized();
            launchSession(this::switchTo);
        });
    }

    @Override
    protected void switchTo() {
        currentFormHandler = formHandlerFactory.build(
                SettingsFormModeHandler.Parameters.builder()
                        .userId(currentUserId)
                        .onSave(this::handleSave)
                        .onCancel(this::closeToList)
                        .build());
        currentFormHandler.activate(layout);
        layout.getBreadcrumbCurrent().setText(i18n().get(SETTINGS_SECTION_TITLE));
    }
}
