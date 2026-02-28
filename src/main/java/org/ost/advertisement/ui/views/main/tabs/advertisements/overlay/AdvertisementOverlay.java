package org.ost.advertisement.ui.views.main.tabs.advertisements.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.services.NotificationService;
import org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementFormOverlayModeHandler;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.advertisement.ui.views.components.overlay.OverlayModeHandler;
import org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementViewOverlayModeHandler;
import org.ost.advertisement.ui.views.components.overlay.BaseOverlay;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.springframework.beans.factory.ObjectProvider;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class AdvertisementOverlay extends BaseOverlay {

    private enum Mode {VIEW, EDIT, CREATE}

    private record OverlaySession(
            Mode mode,
            AdvertisementInfoDto ad,
            @NonNull Runnable onSaved,
            boolean enteredFromView
    ) {
        OverlaySession toView() {
            return new OverlaySession(Mode.VIEW, ad, onSaved, false);
        }

        OverlaySession toEdit() {
            return new OverlaySession(Mode.EDIT, ad, onSaved, true);
        }
    }

    private final transient I18nService                         i18n;
    private final transient NotificationService                 notification;
    private final transient AdvertisementViewOverlayModeHandler.Builder viewModeHandlerBuilder;
    private final transient AdvertisementFormOverlayModeHandler.Builder formModeHandlerBuilder;
    private final transient ConfirmActionDialog.Builder          confirmDiscardBuilder;
    private final transient ObjectProvider<OverlayLayout>        layoutProvider;

    private final OverlayBreadcrumbBackButton breadcrumbBackButton;

    private transient OverlaySession session;
    private OverlayLayout layout;
    private transient AdvertisementFormOverlayModeHandler currentFormHandler;

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

    @Override
    protected void buildContent() {
        addClassName("advertisement-overlay");
        breadcrumbBackButton.configure(OverlayBreadcrumbBackButton.Parameters.builder()
                        .labelKey(MAIN_TAB_ADVERTISEMENTS)
                        .build()).
                addClickListener(_ -> closeToList());
    }

    @Override
    protected void onEsc() {
        handleCancel();
    }

    private void openSession(OverlaySession s) {
        if (layout != null) layout.removeFromParent();
        layout = layoutProvider.getObject();
        layout.setBreadcrumbButton(breadcrumbBackButton);
        session = s;
        switchTo();
        add(layout);
        open();
    }

    private void switchTo() {
        OverlayModeHandler handler = switch (session.mode()) {
            case VIEW -> viewModeHandlerBuilder.build(
                    AdvertisementViewOverlayModeHandler.Parameters.builder()
                            .ad(session.ad())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeToList)
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
            case VIEW -> "";
            case EDIT -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_NEW);
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
            notification.success(ADVERTISEMENT_OVERLAY_NOTIFICATION_SUCCESS);
            session.onSaved().run();
            closeToList();
        } else {
            notification.error(ADVERTISEMENT_OVERLAY_NOTIFICATION_VALIDATION_FAILED);
        }
    }

    private void handleCancel() {
        if (currentFormHandler != null && currentFormHandler.hasChanges()) {
            confirmDiscardBuilder.build(
                    ConfirmActionDialog.Parameters.builder()
                            .titleKey(OVERLAY_UNSAVED_TITLE)
                            .message(i18n.get(OVERLAY_UNSAVED_TEXT))
                            .confirmKey(OVERLAY_UNSAVED_CONFIRM)
                            .cancelKey(OVERLAY_UNSAVED_CANCEL)
                            .onConfirm(this::doCancel)
                            .build()
            ).open();
        } else {
            doCancel();
        }
    }

    private void doCancel() {
        if (session.mode() == Mode.EDIT && session.enteredFromView()) {
            session = session.toView();
            switchTo();
        } else {
            closeToList();
        }
    }
}
