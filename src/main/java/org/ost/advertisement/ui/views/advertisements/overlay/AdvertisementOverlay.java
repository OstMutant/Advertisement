package org.ost.advertisement.ui.views.advertisements.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementBreadcrumbButton;
import org.ost.advertisement.ui.views.advertisements.overlay.modes.FormModeHandler;
import org.ost.advertisement.ui.views.advertisements.overlay.modes.ModeHandler;
import org.ost.advertisement.ui.views.advertisements.overlay.modes.ViewModeHandler;
import org.ost.advertisement.ui.views.components.overlay.BaseOverlay;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.springframework.beans.factory.ObjectProvider;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementOverlay extends BaseOverlay {

    private final transient I18nService                  i18n;
    private final transient NotificationService          notification;
    private final transient ViewModeHandler              viewModeHandler;
    private final transient FormModeHandler              formModeHandler;
    private final transient ObjectProvider<OverlayLayout> layoutProvider;

    private final OverlayAdvertisementBreadcrumbButton breadcrumbButton;

    private final AtomicReference<OverlaySession> session  = new AtomicReference<>();
    private final Map<Mode, ModeHandler>          handlers = new EnumMap<>(Mode.class);

    private OverlayLayout layout;

    public void openForView(AdvertisementInfoDto ad, Runnable onChanged) {
        ensureInitialized();
        open(new OverlaySession(Mode.VIEW, ad, onChanged, false));
    }

    public void openForCreate(Runnable onSaved) {
        ensureInitialized();
        open(new OverlaySession(Mode.CREATE, null, onSaved, false));
    }

    public void openForEdit(AdvertisementInfoDto ad, Runnable onSaved) {
        ensureInitialized();
        open(new OverlaySession(Mode.EDIT, ad, onSaved, false));
    }

    @Override
    protected void buildContent() {
        addClassName("advertisement-overlay");
        viewModeHandler.setCallbacks(this::switchToEdit, this::closeToList);
        formModeHandler.setCallbacks(this::handleSave,   this::handleCancel);

        handlers.put(Mode.VIEW,   viewModeHandler);
        handlers.put(Mode.EDIT,   formModeHandler);
        handlers.put(Mode.CREATE, formModeHandler);
    }

    @Override
    protected void onEsc() {
        handleCancel();
    }

    private void open(OverlaySession s) {
        if (layout != null) layout.removeFromParent();
        layout = layoutProvider.getObject();
        layout.setBreadcrumbButton(breadcrumbButton);
        session.set(s);
        switchTo(s);
        add(layout);
        open();
    }

    private void switchTo(OverlaySession s) {
        ModeHandler active = handlers.get(s.mode());
        handlers.values().stream().distinct()
                .filter(h -> h != active)
                .forEach(ModeHandler::deactivate);
        active.activate(s, layout);

        layout.getBreadcrumbCurrent().setText(switch (s.mode()) {
            case VIEW   -> "";
            case EDIT   -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_NEW);
        });
        layout.getBreadcrumbCurrent().setVisible(s.mode() != Mode.VIEW);
        layout.setBreadcrumbButton(breadcrumbButton);
    }

    private void switchToEdit() {
        if (session.get().ad() == null) return;
        OverlaySession next = session.get().toEdit();
        session.set(next);
        switchTo(next);
    }

    private void handleSave() {
        if (formModeHandler.save()) {
            notification.success(ADVERTISEMENT_OVERLAY_NOTIFICATION_SUCCESS);
            OverlaySession s = session.get();
            if (s.onSaved() != null) s.onSaved().run();
            closeToList();
        } else {
            notification.error(ADVERTISEMENT_OVERLAY_NOTIFICATION_VALIDATION_FAILED);
        }
    }

    private void handleCancel() {
        OverlaySession s = session.get();
        if (s.mode() == Mode.EDIT && s.enteredFromView()) {
            OverlaySession prev = s.toView();
            session.set(prev);
            switchTo(prev);
        } else {
            closeToList();
        }
    }
}