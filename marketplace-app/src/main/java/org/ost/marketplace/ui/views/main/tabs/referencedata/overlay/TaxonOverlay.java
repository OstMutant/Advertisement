package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.TaxonFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.TaxonViewOverlayModeHandler;
import org.ost.platform.taxon.dto.TaxonDto;

import static org.ost.marketplace.services.i18n.I18nKey.MAIN_TAB_REFERENCE_DATA;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class TaxonOverlay extends AbstractEntityOverlay {

    private enum Mode { VIEW, CREATE, EDIT }

    private record OverlaySession(
            @NonNull Mode     mode,
            TaxonDto          taxon,
            @NonNull Runnable onSaved,
            boolean           enteredFromView
    ) {
        OverlaySession toEdit() { return new OverlaySession(Mode.EDIT, taxon, onSaved, true); }
        OverlaySession toView() { return new OverlaySession(Mode.VIEW, taxon, onSaved, false); }
    }

    @Getter private final EntityOverlaySupport support;
    private final UiComponentFactory<TaxonViewOverlayModeHandler> viewModeHandlerFactory;
    private final UiComponentFactory<TaxonFormOverlayModeHandler> formModeHandlerFactory;

    private OverlaySession              session;
    private TaxonFormOverlayModeHandler currentFormHandler;

    @Override protected String  getOverlayCssClass()   { return "taxon-overlay"; }
    @Override protected I18nKey getBreadcrumbLabelKey() { return MAIN_TAB_REFERENCE_DATA; }
    @Override protected boolean hasUnsavedChanges()     { return currentFormHandler != null && currentFormHandler.hasChanges(); }

    public void openForView(@NonNull TaxonDto taxon, @NonNull Runnable onSaved) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, taxon, onSaved, false));
    }

    public void openForCreate(@NonNull Runnable onSaved) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.CREATE, null, onSaved, false));
    }

    public void openForEdit(@NonNull TaxonDto taxon, @NonNull Runnable onSaved) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, taxon, onSaved, false));
    }

    private void openSession(OverlaySession s) {
        session = s;
        launchSession(this::switchTo);
    }

    @Override
    protected void switchTo() {
        currentFormHandler = null;

        OverlayModeHandler handler = switch (session.mode()) {
            case VIEW -> viewModeHandlerFactory.build(
                    TaxonViewOverlayModeHandler.Parameters.builder()
                            .taxon(session.taxon())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeToList)
                            .build());
            case CREATE, EDIT -> {
                TaxonFormOverlayModeHandler.Mode handlerMode = session.mode() == Mode.CREATE
                        ? TaxonFormOverlayModeHandler.Mode.CREATE
                        : TaxonFormOverlayModeHandler.Mode.EDIT;
                currentFormHandler = formModeHandlerFactory.build(
                        TaxonFormOverlayModeHandler.Parameters.builder()
                                .taxon(session.taxon())
                                .mode(handlerMode)
                                .onSave(this::handleSave)
                                .onCancel(this::handleCancel)
                                .build());
                yield currentFormHandler;
            }
        };

        handler.activate(layout);

        String breadcrumb = switch (session.mode()) {
            case VIEW   -> "";
            case EDIT   -> i18n().get(I18nKey.TAXON_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n().get(I18nKey.TAXON_OVERLAY_TITLE_NEW);
        };
        layout.getBreadcrumbCurrent().setText(breadcrumb);
        layout.getBreadcrumbCurrent().setVisible(!breadcrumb.isEmpty());
    }

    private void switchToEdit() {
        if (session.taxon() == null) return;
        session = session.toEdit();
        launchSession(this::switchTo);
    }

    private void handleSave() {
        try {
            if (currentFormHandler.save()) {
                notification().success(I18nKey.TAXON_OVERLAY_NOTIFICATION_SUCCESS);
                session.onSaved().run();
                currentFormHandler.afterSave(true);
            } else {
                notification().error(I18nKey.TAXON_OVERLAY_NOTIFICATION_VALIDATION_FAILED);
                currentFormHandler.afterSave(false);
            }
        } catch (Exception e) {
            notification().error(I18nKey.TAXON_OVERLAY_NOTIFICATION_SAVE_ERROR, e.getMessage());
            currentFormHandler.afterSave(false);
        }
    }

    @Override
    protected void doCancel() {
        if (currentFormHandler != null) currentFormHandler.discardChanges();
        if (session.enteredFromView()) {
            session = session.toView();
            launchSession(this::switchTo);
        } else {
            closeToList();
        }
    }
}
