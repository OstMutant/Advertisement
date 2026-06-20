package org.ost.marketplace.ui.views.main.tabs.advertisements.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementViewOverlayModeHandler;
import org.ost.marketplace.ui.core.UiComponentFactory;

import static org.ost.marketplace.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class AdvertisementOverlay extends AbstractEntityOverlay {

    private enum Mode {VIEW, EDIT, CREATE}

    private record OverlaySession(
            Mode mode,
            AdvertisementInfoDto ad,
            @NonNull Runnable onSaved,
            boolean enteredFromView
    ) {
        OverlaySession toView() { return new OverlaySession(Mode.VIEW, ad, onSaved, false); }
        OverlaySession toEdit() { return new OverlaySession(Mode.EDIT, ad, onSaved, true); }
        OverlaySession withAd(AdvertisementInfoDto fresh) { return new OverlaySession(mode, fresh, onSaved, enteredFromView); }
    }

    @Getter private final EntityOverlaySupport  support;
    private final UiComponentFactory<AdvertisementViewOverlayModeHandler> viewModeHandlerFactory;
    private final UiComponentFactory<AdvertisementFormOverlayModeHandler> formModeHandlerFactory;

    private OverlaySession                      session;
    private AdvertisementFormOverlayModeHandler currentFormHandler;

    @Override protected String  getOverlayCssClass()    { return "advertisement-overlay"; }
    @Override protected I18nKey              getBreadcrumbLabelKey() { return MAIN_TAB_ADVERTISEMENTS; }
    @Override protected boolean              hasUnsavedChanges()  { return currentFormHandler != null && currentFormHandler.hasChanges(); }

    public void openForView(AdvertisementInfoDto ad, Runnable onChanged) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, ad, onChanged, false));
    }

    public void openForCreate(Runnable onSaved) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.CREATE, null, onSaved, false));
    }

    public void openForEdit(AdvertisementInfoDto ad, Runnable onSaved) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, ad, onSaved, false));
    }

    private void openSession(OverlaySession s) {
        session = s;
        launchSession(this::switchTo);
    }

    @Override
    protected void switchTo() {
        OverlayModeHandler handler = switch (session.mode()) {
            case VIEW -> viewModeHandlerFactory.build(
                    AdvertisementViewOverlayModeHandler.Parameters.builder()
                            .ad(session.ad())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeAndRefresh)
                            .build());
            case EDIT, CREATE -> {
                currentFormHandler = formModeHandlerFactory.build(
                        AdvertisementFormOverlayModeHandler.Parameters.builder()
                                .ad(session.ad())
                                .onSave(this::handleSave)
                                .onCancel(this::handleCancel)
                                .build());
                yield currentFormHandler;
            }
        };

        handler.activate(layout);

        layout.getBreadcrumbCurrent().setText(switch (session.mode()) {
            case VIEW   -> "";
            case EDIT   -> i18n().get(ADVERTISEMENT_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n().get(ADVERTISEMENT_OVERLAY_TITLE_NEW);
        });
        layout.getBreadcrumbCurrent().setVisible(session.mode() != Mode.VIEW);
    }

    private void switchToEdit() {
        if (session.ad() == null) return;
        session = session.toEdit();
        switchTo();
    }

    private void handleSave() {
        try {
            if (currentFormHandler.save()) {
                notification().success(ADVERTISEMENT_OVERLAY_NOTIFICATION_SUCCESS);
                session.onSaved().run();
                if (session.mode() == Mode.EDIT) {
                    currentFormHandler.afterSave(true);
                    AdvertisementInfoDto fresh = currentFormHandler.getSavedInfoDto();
                    if (fresh != null) session = session.withAd(fresh);
                } else {
                    closeToList();
                }
            } else {
                notification().error(ADVERTISEMENT_OVERLAY_NOTIFICATION_VALIDATION_FAILED);
                currentFormHandler.afterSave(false);
            }
        } catch (Exception e) {
            notification().error(ADVERTISEMENT_OVERLAY_NOTIFICATION_SAVE_ERROR, e.getMessage());
            currentFormHandler.afterSave(false);
        }
    }

    private void closeAndRefresh() {
        session.onSaved().run();
        closeToList();
    }

    @Override
    protected void doCancel() {
        if (currentFormHandler != null) {
            currentFormHandler.discard();
        }
        if (session.mode() == Mode.EDIT && session.enteredFromView()) {
            session = session.toView();
            switchTo();
        } else {
            closeToList();
        }
    }
}
