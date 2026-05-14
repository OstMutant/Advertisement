package org.ost.advertisement.ui.views.main.tabs.users.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.common.I18nKey;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.user.UserService;
import org.ost.advertisement.services.auth.AuthContextService;
import org.ost.advertisement.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.advertisement.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.advertisement.ui.views.components.overlay.OverlayModeHandler;
import org.ost.advertisement.ui.views.main.tabs.users.overlay.modes.UserFormOverlayModeHandler;
import org.ost.advertisement.ui.views.main.tabs.users.overlay.modes.UserViewOverlayModeHandler;

import static org.ost.advertisement.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class UserOverlay extends AbstractEntityOverlay {

    private enum Mode {VIEW, EDIT}

    private record OverlaySession(
            Mode mode,
            @NonNull User user,
            @NonNull Runnable onSaved,
            boolean enteredFromView
    ) {
        OverlaySession toEdit() { return new OverlaySession(Mode.EDIT, user, onSaved, true); }
        OverlaySession toView() { return new OverlaySession(Mode.VIEW, user, onSaved, false); }
    }

    @Getter private final transient EntityOverlaySupport               support;
    private final transient UserService                        userService;
    private final transient AuthContextService                 authContextService;
    private final transient UserViewOverlayModeHandler.Builder viewModeHandlerBuilder;
    private final transient UserFormOverlayModeHandler.Builder formModeHandlerBuilder;

    private transient OverlaySession            session;
    private transient UserFormOverlayModeHandler currentFormHandler;

    @Override protected String  getOverlayCssClass()   { return "user-overlay"; }
    @Override protected I18nKey              getBreadcrumbLabelKey() { return MAIN_TAB_USERS; }
    @Override protected boolean              hasUnsavedChanges()    { return currentFormHandler != null && currentFormHandler.hasChanges(); }

    public void openForView(User user, Runnable onChanged) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, user, onChanged, false));
    }

    public void openForEdit(User user, Runnable onSaved) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, user, onSaved, false));
    }

    private void openSession(OverlaySession s) {
        session = s;
        launchSession(this::switchTo);
    }

    @Override
    protected void switchTo() {
        OverlayModeHandler handler = switch (session.mode()) {
            case VIEW -> viewModeHandlerBuilder.build(
                    UserViewOverlayModeHandler.Parameters.builder()
                            .user(session.user())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeToList)
                            .onRestoreUser(this::handleRestoreUser)
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

        layout.getBreadcrumbCurrent().setText(session.mode() == Mode.EDIT ? session.user().getName() : "");
        layout.getBreadcrumbCurrent().setVisible(session.mode() == Mode.EDIT);
    }

    private void switchToEdit() {
        session = session.toEdit();
        switchTo();
    }

    private void handleSave() {
        try {
            if (currentFormHandler.save()) {
                notification().success(USER_DIALOG_NOTIFICATION_SUCCESS);
                session.onSaved().run();
                if (session.enteredFromView()) {
                    Long savedId = currentFormHandler.getSavedUserId();
                    userService.findById(savedId).ifPresentOrElse(freshUser -> {
                        session = new OverlaySession(Mode.VIEW, freshUser, session.onSaved(), false);
                        switchTo();
                    }, this::closeToList);
                } else {
                    closeToList();
                }
            } else {
                notification().error(USER_DIALOG_NOTIFICATION_VALIDATION_FAILED);
            }
        } catch (Exception e) {
            notification().error(USER_DIALOG_NOTIFICATION_SAVE_ERROR, e.getMessage());
        }
    }

    private void handleRestoreUser(Long snapshotId) {
        Long actingUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        userService.restoreToSnapshot(snapshotId, actingUserId).ifPresentOrElse(
                restoredUser -> {
                    notification().success(USER_DIALOG_NOTIFICATION_SUCCESS);
                    session.onSaved().run();
                    openSession(new OverlaySession(Mode.VIEW, restoredUser, session.onSaved(), false));
                },
                () -> notification().error(USER_DIALOG_NOTIFICATION_SAVE_ERROR)
        );
    }

    @Override
    protected void doCancel() {
        if (session.mode() == Mode.EDIT && session.enteredFromView()) {
            session = session.toView();
            switchTo();
        } else {
            closeToList();
        }
    }
}
