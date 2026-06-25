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

import static org.ost.marketplace.services.i18n.I18nKey.HEADER_HOME;
import static org.ost.marketplace.services.i18n.I18nKey.SETTINGS_SAVED_SUCCESS;
import static org.ost.marketplace.services.i18n.I18nKey.SETTINGS_SECTION_TITLE;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class SettingsOverlay extends AbstractEntityOverlay {

    @Getter private final EntityOverlaySupport support;
    private final AuthContextService           authContextService;
    private final UiComponentFactory<SettingsFormModeHandler> formHandlerFactory;

    private Long                    currentUserId;
    private SettingsFormModeHandler currentHandler;

    @Override protected String  getOverlayCssClass()   { return "settings-overlay"; }
    @Override protected I18nKey getBreadcrumbLabelKey() { return HEADER_HOME; }
    @Override protected boolean hasUnsavedChanges()    { return currentHandler != null && currentHandler.hasChanges(); }

    public void openSettings() {
        authContextService.getCurrentUser().ifPresent(user -> {
            currentUserId = user.id();
            ensureInitialized();
            launchSession(this::switchTo);
        });
    }

    @Override
    protected void switchTo() {
        currentHandler = formHandlerFactory.build(
                SettingsFormModeHandler.Parameters.builder()
                        .userId(currentUserId)
                        .onSave(this::handleSave)
                        .onCancel(this::closeToList)
                        .build());
        currentHandler.activate(layout);
        layout.getBreadcrumbCurrent().setText(i18n().get(SETTINGS_SECTION_TITLE));
    }

    @Override
    protected void doCancel() {
        closeToList();
    }

    private void handleSave() {
        try {
            if (currentHandler.save()) {
                notification().success(SETTINGS_SAVED_SUCCESS);
                currentHandler.afterSave(true);
            } else {
                currentHandler.afterSave(false);
            }
        } catch (Exception e) {
            notification().error(e.getMessage());
            currentHandler.afterSave(false);
        }
    }
}
