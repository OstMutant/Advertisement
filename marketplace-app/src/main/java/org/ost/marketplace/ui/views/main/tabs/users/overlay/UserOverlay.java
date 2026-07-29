package org.ost.marketplace.ui.views.main.tabs.users.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.platform.user.dto.UserDto;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.overlay.modes.UserFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.overlay.modes.UserViewOverlayModeHandler;
import org.ost.marketplace.ui.core.UiComponentFactory;

import java.util.List;
import java.util.function.Consumer;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class UserOverlay extends AbstractEntityOverlay<UserFormOverlayModeHandler> {

    private enum Mode {VIEW, EDIT}

    private record OverlaySession(
            Mode mode,
            @NonNull UserDto user,
            @NonNull Consumer<UserDto> onUpdated,
            @NonNull Runnable onClosed,
            boolean enteredFromView
    ) {
        OverlaySession toEdit() { return new OverlaySession(Mode.EDIT, user, onUpdated, onClosed, true); }
        OverlaySession toView() { return new OverlaySession(Mode.VIEW, user, onUpdated, onClosed, false); }
        OverlaySession withUser(UserDto fresh) { return new OverlaySession(mode, fresh, onUpdated, onClosed, enteredFromView); }
    }

    @Getter private final EntityOverlaySupport support;
    private final UiComponentFactory<UserViewOverlayModeHandler> viewModeHandlerFactory;
    private final UiComponentFactory<UserFormOverlayModeHandler> formModeHandlerFactory;

    private OverlaySession session;

    @Override protected String  getOverlayCssClass()   { return "user-overlay"; }
    @Override protected I18nKey getBreadcrumbLabelKey() { return MAIN_TAB_USERS; }

    @Override protected boolean isEditMode()      { return session.mode() == Mode.EDIT; }
    @Override protected boolean enteredFromView() { return session.enteredFromView(); }

    @Override
    protected SaveConfig saveConfig() {
        return new SaveConfig(
                USER_DIALOG_NOTIFICATION_SUCCESS,
                USER_DIALOG_NOTIFICATION_VALIDATION_FAILED,
                USER_DIALOG_NOTIFICATION_SAVE_ERROR,
                USER_DIALOG_NOTIFICATION_CONFLICT);
    }

    @Override
    protected void proceed() {
        UserDto fresh = currentFormHandler.getSavedUser();
        if (fresh != null) {
            session = session.withUser(fresh);
            session.onUpdated().accept(fresh);
        }
    }

    @Override
    protected void afterDiscard() {
        if (session.mode() == Mode.EDIT && session.enteredFromView()) {
            session = session.toView();
            switchTo();
        } else {
            closeToList();
        }
    }

    public void openForView(UserDto user, Consumer<UserDto> onUpdated, Runnable onClosed) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, user, onUpdated, onClosed, false));
    }

    public void openForEdit(UserDto user, Consumer<UserDto> onUpdated, Runnable onClosed) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, user, onUpdated, onClosed, false));
    }

    private void openSession(OverlaySession s) {
        session = s;
        launchSession(this::switchTo);
    }

    @Override
    protected void switchTo() {
        currentFormHandler = null;
        List<BreadcrumbStep> breadcrumbSteps = buildBreadcrumbSteps();
        layout.setBreadcrumbLinks(buildBreadcrumbLinks(breadcrumbSteps));
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
                                .breadcrumbSteps(breadcrumbSteps)
                                .build());
                yield currentFormHandler;
            }
        };

        handler.activate(layout);

        layout.getBreadcrumbCurrent().setText(session.mode() == Mode.EDIT ? session.user().name() : i18n().get(OVERLAY_BREADCRUMB_VIEW));
    }

    private void switchToEdit() {
        session = session.toEdit();
        switchTo();
    }

    @Override
    protected void closeToList() {
        session.onClosed().run();
        super.closeToList();
    }
}
