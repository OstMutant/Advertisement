package org.ost.advertisement.ui.views.main.tabs.advertisements.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.common.I18nKey;
import org.ost.advertisement.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.advertisement.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.advertisement.ui.views.components.overlay.OverlayModeHandler;
import org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementFormOverlayModeHandler;
import org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementViewOverlayModeHandler;

import static org.ost.advertisement.common.I18nKey.*;

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
    }

    @Getter private final EntityOverlaySupport                        support;
    private final transient AdvertisementService              advertisementService;
    private final transient AdvertisementViewOverlayModeHandler.Builder viewModeHandlerBuilder;
    private final transient AdvertisementFormOverlayModeHandler.Builder formModeHandlerBuilder;

    private transient OverlaySession                      session;
    private transient AdvertisementFormOverlayModeHandler currentFormHandler;

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
            case VIEW -> viewModeHandlerBuilder.build(
                    AdvertisementViewOverlayModeHandler.Parameters.builder()
                            .ad(session.ad())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeToList)
                            .onRestore(this::handleRestore)
                            .build());
            case EDIT, CREATE -> {
                currentFormHandler = formModeHandlerBuilder.build(
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
        if (currentFormHandler.save()) {
            notification().success(ADVERTISEMENT_OVERLAY_NOTIFICATION_SUCCESS);
            session.onSaved().run();
            if (session.enteredFromView()) {
                Long savedId = currentFormHandler.getSavedAdvertisement().getId();
                advertisementService.findById(savedId).ifPresentOrElse(freshAd -> {
                    session = new OverlaySession(Mode.VIEW, freshAd, session.onSaved(), false);
                    switchTo();
                }, this::closeToList);
            } else {
                closeToList();
            }
        } else {
            notification().error(ADVERTISEMENT_OVERLAY_NOTIFICATION_VALIDATION_FAILED);
        }
    }

    private void handleRestore(Long snapshotId) {
        try {
            if (advertisementService.restore(session.ad().getId(), snapshotId)) {
                notification().success(ADVERTISEMENT_RESTORED_SUCCESS);
                session.onSaved().run();
                advertisementService.findById(session.ad().getId()).ifPresent(freshAd -> {
                    session = new OverlaySession(Mode.VIEW, freshAd, session.onSaved(), false);
                    switchTo();
                });
            }
        } catch (Exception e) {
            notification().error(ADVERTISEMENT_OVERLAY_NOTIFICATION_SAVE_ERROR);
        }
    }

    @Override
    protected void doCancel() {
        if (session.mode() == Mode.CREATE && currentFormHandler != null) {
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
