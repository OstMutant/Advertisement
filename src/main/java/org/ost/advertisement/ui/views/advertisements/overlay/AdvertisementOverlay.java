package org.ost.advertisement.ui.views.advertisements.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementBreadcrumbButton;
import org.ost.advertisement.ui.views.advertisements.overlay.modes.FormModeHandler;
import org.ost.advertisement.ui.views.advertisements.overlay.modes.ViewModeHandler;
import org.ost.advertisement.ui.views.components.overlay.BaseOverlay;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.ost.advertisement.constants.I18nKey.*;

/**
 * Full-viewport overlay (position:fixed).
 * Delegates all mode-specific rendering to ModeHandler implementations:
 *   VIEW   → ViewModeHandler
 *   EDIT   → FormModeHandler
 *   CREATE → FormModeHandler
 * To add a new mode: implement ModeHandler, register it in buildContent().
 */
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementOverlay extends BaseOverlay {

    private final transient I18nService         i18n;
    private final transient NotificationService notification;

    private final transient ViewModeHandler viewModeHandler;
    private final transient FormModeHandler formModeHandler;

    private final OverlayAdvertisementBreadcrumbButton breadcrumbButton;

    @Getter
    private final OverlayLayout layout;

    private final AtomicReference<OverlaySession> session  = new AtomicReference<>();
    private final Map<Mode, ModeHandler>          handlers = new EnumMap<>(Mode.class);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void openForView(AdvertisementInfoDto ad, Runnable onChanged) {
        ensureInitialized();
        open(OverlaySession.forView(ad, onChanged));
    }

    public void openForCreate(Runnable onSaved) {
        ensureInitialized();
        open(OverlaySession.forCreate(onSaved));
    }

    public void openForEdit(AdvertisementInfoDto ad, Runnable onSaved) {
        ensureInitialized();
        open(OverlaySession.forEdit(ad, onSaved));
    }

    // -------------------------------------------------------------------------
    // BaseOverlay contract
    // -------------------------------------------------------------------------

    @Override
    protected void buildContent(OverlayLayout l) {
        addClassName("advertisement-overlay");
        breadcrumbButton.addClickListener(_ -> closeToList());
        l.setBreadcrumbButton(breadcrumbButton);

        viewModeHandler.configure(l, this::switchToEdit, this::closeToList);
        formModeHandler.configure(l, this::handleSave,   this::handleCancel);

        handlers.put(Mode.VIEW,   viewModeHandler);
        handlers.put(Mode.EDIT,   formModeHandler);
        handlers.put(Mode.CREATE, formModeHandler);

        // init order determines DOM order inside headerActions and body containers
        viewModeHandler.init();
        formModeHandler.init();
    }

    @Override
    protected void onEsc() {
        handleCancel();
    }

    // -------------------------------------------------------------------------
    // Mode switching
    // -------------------------------------------------------------------------

    private void open(OverlaySession s) {
        session.set(s);
        switchTo(s);
        open();
    }

    private void switchTo(OverlaySession s) {
        ModeHandler active = handlers.get(s.mode());
        handlers.values().stream().distinct()
                .filter(h -> h != active)
                .forEach(ModeHandler::deactivate);
        active.activate(s);

        layout.getBreadcrumbCurrent().setText(switch (s.mode()) {
            case VIEW   -> "";
            case EDIT   -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n.get(ADVERTISEMENT_OVERLAY_TITLE_NEW);
        });
        layout.getBreadcrumbCurrent().setVisible(s.mode() != Mode.VIEW);
    }

    // Entered EDIT from within VIEW — cancel must return to VIEW, not close.
    private void switchToEdit() {
        if (session.get().ad() == null) return;
        OverlaySession next = session.get().toEdit();
        session.set(next);
        switchTo(next);
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

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