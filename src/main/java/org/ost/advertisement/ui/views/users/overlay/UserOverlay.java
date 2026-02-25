package org.ost.advertisement.ui.views.users.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.views.components.overlay.BaseOverlay;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.advertisement.ui.views.users.overlay.modes.UserFormModeHandler;
import org.ost.advertisement.ui.views.users.overlay.modes.UserViewModeHandler;
import org.springframework.beans.factory.ObjectProvider;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class UserOverlay extends BaseOverlay {

    private enum Mode { VIEW, EDIT }

    private record OverlaySession(
            Mode             mode,
            @NonNull User    user,
            @NonNull Runnable onSaved
    ) {
        OverlaySession toEdit() {
            return new OverlaySession(Mode.EDIT, user, onSaved);
        }

        OverlaySession toView() {
            return new OverlaySession(Mode.VIEW, user, onSaved);
        }
    }

    private final NotificationService              notification;
    private final UserViewModeHandler.Builder      viewModeHandlerBuilder;
    private final UserFormModeHandler.Builder      formModeHandlerBuilder;
    private final ObjectProvider<OverlayLayout>    layoutProvider;

    private final OverlayBreadcrumbBackButton breadcrumbBackButton;

    private OverlaySession           session;
    private OverlayLayout            layout;
    private UserFormModeHandler      currentFormHandler;

    public void openForView(User user, Runnable onChanged) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, user, onChanged));
    }

    public void openForEdit(User user, Runnable onSaved) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, user, onSaved));
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
        switch (session.mode()) {
            case VIEW -> viewModeHandlerBuilder.build(
                    UserViewModeHandler.Parameters.builder()
                            .user(session.user())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeToList)
                            .build())
                    .activate(layout);
            case EDIT -> {
                currentFormHandler = formModeHandlerBuilder.build(
                        UserFormModeHandler.Parameters.builder()
                                .user(session.user())
                                .onSave(this::handleSave)
                                .onCancel(this::handleCancel)
                                .build());
                currentFormHandler.activate(layout);
            }
        }
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
        session = session.toView();
        switchTo();
    }
}
