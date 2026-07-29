package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.TaxonFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.TaxonViewOverlayModeHandler;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.spi.TaxonPort;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class TaxonOverlay extends AbstractEntityOverlay<TaxonFormOverlayModeHandler> {

    private enum Mode { VIEW, CREATE, EDIT }

    private record OverlaySession(
            @NonNull Mode               mode,
            TaxonDto                    taxon,
            @NonNull Consumer<TaxonDto> onUpdated,
            @NonNull Runnable           onListChanged,
            boolean                     enteredFromView
    ) {
        OverlaySession toEdit()                  { return new OverlaySession(Mode.EDIT, taxon, onUpdated, onListChanged, true); }
        OverlaySession toView()                  { return new OverlaySession(Mode.VIEW, taxon, onUpdated, onListChanged, false); }
        OverlaySession withTaxon(TaxonDto fresh) { return new OverlaySession(mode, fresh, onUpdated, onListChanged, enteredFromView); }
    }

    @Getter private final EntityOverlaySupport support;
    private final UiComponentFactory<TaxonViewOverlayModeHandler> viewModeHandlerFactory;
    private final UiComponentFactory<TaxonFormOverlayModeHandler> formModeHandlerFactory;
    private final ComponentFactory<TaxonPort>                     taxonPortFactory;

    private OverlaySession session;

    @Override protected String  getOverlayCssClass()   { return "taxon-overlay"; }
    @Override protected I18nKey getBreadcrumbLabelKey() { return MAIN_TAB_REFERENCE_DATA; }

    @Override protected boolean isEditMode()      { return session.mode() == Mode.EDIT; }
    @Override protected boolean enteredFromView() { return session.enteredFromView(); }

    @Override
    protected SaveConfig saveConfig() {
        return new SaveConfig(
                TAXON_OVERLAY_NOTIFICATION_SUCCESS,
                TAXON_OVERLAY_NOTIFICATION_VALIDATION_FAILED,
                TAXON_OVERLAY_NOTIFICATION_SAVE_ERROR,
                TAXON_OVERLAY_NOTIFICATION_CONFLICT);
    }

    @Override
    protected void proceed() {
        Long savedId = currentFormHandler.getSavedTaxonId();
        TaxonDto fresh = savedId == null ? null : taxonPortFactory.findIfAvailable()
                .flatMap(p -> p.findById(savedId, Locale.ENGLISH))
                .orElse(null);
        if (fresh == null) return;

        session = session.withTaxon(fresh);
        if (session.mode() == Mode.EDIT) {
            session.onUpdated().accept(fresh);
        } else {
            session.onListChanged().run();
        }
    }

    @Override
    protected void afterDiscard() {
        if (session.enteredFromView()) {
            session = session.toView();
            switchTo();
        } else {
            closeToList();
        }
    }

    public void openForView(@NonNull TaxonDto taxon, @NonNull Consumer<TaxonDto> onUpdated) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, taxon, onUpdated, () -> {}, false));
    }

    public void openForCreate(@NonNull Runnable onListChanged) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.CREATE, null, _ -> {}, onListChanged, false));
    }

    public void openForEdit(@NonNull TaxonDto taxon, @NonNull Consumer<TaxonDto> onUpdated) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, taxon, onUpdated, () -> {}, false));
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
                                .breadcrumbSteps(breadcrumbSteps)
                                .build());
                yield currentFormHandler;
            }
        };

        handler.activate(layout);

        layout.getBreadcrumbCurrent().setText(switch (session.mode()) {
            case VIEW   -> i18n().get(OVERLAY_BREADCRUMB_VIEW);
            case EDIT   -> i18n().get(TAXON_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n().get(TAXON_OVERLAY_TITLE_NEW);
        });
    }

    private void switchToEdit() {
        if (session.taxon() == null) return;
        session = session.toEdit();
        switchTo();
    }
}
