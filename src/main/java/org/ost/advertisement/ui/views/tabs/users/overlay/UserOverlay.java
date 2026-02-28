package org.ost.advertisement.ui.views.tabs.users.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.services.NotificationService;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.advertisement.ui.views.components.overlay.BaseOverlay;
import org.ost.advertisement.ui.views.components.overlay.OverlayModeHandler;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.advertisement.ui.views.tabs.users.overlay.modes.UserFormOverlayModeHandler;
import org.ost.advertisement.ui.views.tabs.users.overlay.modes.UserViewOverlayModeHandler;
import org.springframework.beans.factory.ObjectProvider;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class UserOverlay extends BaseOverlay {

    private enum Mode {VIEW, EDIT}

    private record OverlaySession(
            Mode mode,
            @NonNull User user,
            @NonNull Runnable onSaved,
            boolean enteredFromView
    ) {
        OverlaySession toEdit() {
            return new OverlaySession(Mode.EDIT, user, onSaved, true);
        }

        OverlaySession toView() {
            return new OverlaySession(Mode.VIEW, user, onSaved, false);
        }
    }

    private final transient I18nService                  i18n;
    private final transient NotificationService          notification;
    private final transient UserViewOverlayModeHandler.Builder  viewModeHandlerBuilder;
    private final transient UserFormOverlayModeHandler.Builder  formModeHandlerBuilder;
    private final transient ConfirmActionDialog.Builder  confirmDiscardBuilder;
    private final transient ObjectProvider<OverlayLayout> layoutProvider;

    private final OverlayBreadcrumbBackButton breadcrumbBackButton;

    private transient OverlaySession session;
    private OverlayLayout layout;
    private transient UserFormOverlayModeHandler currentFormHandler;

    public void openForView(User user, Runnable onChanged) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, user, onChanged, false));
    }

    public void openForEdit(User user, Runnable onSaved) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, user, onSaved, false));
    }

    @Override
    protected void buildContent() {
        addClassName("user-overlay");
        breadcrumbBackButton.configure(OverlayBreadcrumbBackButton.Parameters.builder()
                        .labelKey(MAIN_TAB_USERS)
                        .build())
                .addClickListener(_ -> closeToList());
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
                    UserViewOverlayModeHandler.Parameters.builder()
                            .user(session.user())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeToList)
                            .build());
            case EDIT -> {
                currentFormHandler = formModeHandlerBuilder.build(
                        UserFormOverlayModeHandler.Parameters.builder()
                                .user(session.user())
                                .onSave(this::handleSave)
                                .onCancel(this::handleCancel)
                                .build());
                yield currentFormHandler;
            }
        };

        handler.activate(layout);

        layout.getBreadcrumbCurrent().setText(session.mode() == Mode.EDIT
                ? session.user().getName() : "");
        layout.getBreadcrumbCurrent().setVisible(session.mode() == Mode.EDIT);
    }

    private void switchToEdit() {
        session = session.toEdit();
        switchTo();
    }

    private void handleSave() {
        if (currentFormHandler.save()) {
            notification.success(USER_DIALOG_NOTIFICATION_SUCCESS);
            session.onSaved().run();
            closeToList();
        } else {
            notification.error(USER_DIALOG_NOTIFICATION_VALIDATION_FAILED);
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
