package org.ost.marketplace.ui.views.main.tabs.users.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.overlay.modes.UserFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.overlay.modes.UserViewOverlayModeHandler;
import org.ost.platform.core.ComponentFactory;

import static org.ost.marketplace.common.I18nKey.*;

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
        OverlaySession withUser(User fresh) { return new OverlaySession(mode, fresh, onSaved, enteredFromView); }
    }

    @Getter private final EntityOverlaySupport support;
    private final AuthContextService           authContextService;
    private final ComponentFactory<UserViewOverlayModeHandler> viewModeHandlerFactory;
    private final ComponentFactory<UserFormOverlayModeHandler> formModeHandlerFactory;

    private OverlaySession            session;
    private UserFormOverlayModeHandler currentFormHandler;

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
            case VIEW -> viewModeHandlerFactory.build(
                    UserViewOverlayModeHandler.Parameters.builder()
                            .user(session.user())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeToList)
                            .build());
            case EDIT -> {
                currentFormHandler = formModeHandlerFactory.build(
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
                currentFormHandler.afterSave(true);
                User fresh = currentFormHandler.getSavedUser();
                if (fresh != null) session = session.withUser(fresh);
            } else {
                notification().error(USER_DIALOG_NOTIFICATION_VALIDATION_FAILED);
                currentFormHandler.afterSave(false);
            }
        } catch (Exception e) {
            notification().error(USER_DIALOG_NOTIFICATION_SAVE_ERROR, e.getMessage());
            currentFormHandler.afterSave(false);
        }
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
