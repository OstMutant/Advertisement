package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay;

import lombok.NonNull;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.AbstractTaxonFormOverlayModeHandler;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static org.ost.marketplace.services.i18n.I18nKey.MAIN_TAB_REFERENCE_DATA;
import static org.ost.marketplace.services.i18n.I18nKey.OVERLAY_BREADCRUMB_VIEW;

/** Shared open/switch/save-proceed lifecycle for a {@link TaxonDto}-backed overlay, parameterized by the concrete City/Category subclass's mode-handler factories and title keys. */
@SuppressWarnings("java:S110")
public abstract class AbstractTaxonOverlay<H extends AbstractTaxonFormOverlayModeHandler<?>>
        extends AbstractEntityOverlay<H> implements TaxonManagementOverlay {

    protected enum Mode { VIEW, CREATE, EDIT }

    protected record OverlaySession(
            @NonNull Mode               mode,
            TaxonDto                    entity,
            @NonNull Consumer<TaxonDto> onUpdated,
            @NonNull Runnable           onListChanged,
            boolean                     enteredFromView
    ) {
        OverlaySession toEdit()                   { return new OverlaySession(Mode.EDIT, entity, onUpdated, onListChanged, true); }
        OverlaySession toView()                   { return new OverlaySession(Mode.VIEW, entity, onUpdated, onListChanged, false); }
        OverlaySession withEntity(TaxonDto fresh) { return new OverlaySession(mode, fresh, onUpdated, onListChanged, enteredFromView); }
    }

    protected OverlaySession session;

    protected abstract TaxonCatalogService getTaxonCatalogService();
    protected abstract OverlayModeHandler  buildViewHandler(TaxonDto entity, Runnable onEdit, Runnable onClose);
    protected abstract H                   buildFormHandler(TaxonDto entity, Mode mode, List<BreadcrumbStep> breadcrumbSteps);
    protected abstract I18nKey             getTitleEdit();
    protected abstract I18nKey             getTitleNew();

    @Override protected I18nKey getBreadcrumbLabelKey() { return MAIN_TAB_REFERENCE_DATA; }

    protected AbstractTaxonFormOverlayModeHandler.Mode toHandlerMode(Mode mode) {
        return mode == Mode.CREATE ? AbstractTaxonFormOverlayModeHandler.Mode.CREATE : AbstractTaxonFormOverlayModeHandler.Mode.EDIT;
    }

    @Override protected boolean isEditMode()      { return session.mode() == Mode.EDIT; }
    @Override protected boolean enteredFromView() { return session.enteredFromView(); }

    @Override
    protected void proceed() {
        if (session.mode() != Mode.EDIT) {
            // CREATE: onListChanged() re-queries the whole list from the DB anyway -- no refetch needed here.
            session.onListChanged().run();
            return;
        }
        Long savedId = currentFormHandler.getSavedEntityId();
        Optional<TaxonDto> fresh = savedId == null ? Optional.empty()
                : getTaxonCatalogService().findById(savedId, Locale.ENGLISH);
        applyFreshOrFallback(fresh,
                f -> { session = session.withEntity(f); session.onUpdated().accept(f); },
                this::closeToList);
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

    @Override
    public void openForView(@NonNull TaxonDto entity, @NonNull Consumer<TaxonDto> onUpdated) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, entity, onUpdated, () -> {}, false));
    }

    @Override
    public void openForCreate(@NonNull Runnable onListChanged) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.CREATE, null, _ -> {}, onListChanged, false));
    }

    @Override
    public void openForEdit(@NonNull TaxonDto entity, @NonNull Consumer<TaxonDto> onUpdated) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, entity, onUpdated, () -> {}, false));
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
            case VIEW -> buildViewHandler(session.entity(), this::switchToEdit, this::closeToList);
            case CREATE, EDIT -> {
                currentFormHandler = buildFormHandler(session.entity(), session.mode(), breadcrumbSteps);
                yield currentFormHandler;
            }
        };

        handler.activate(layout);

        layout.getBreadcrumbCurrent().setText(switch (session.mode()) {
            case VIEW   -> i18n().get(OVERLAY_BREADCRUMB_VIEW);
            case EDIT   -> i18n().get(getTitleEdit());
            case CREATE -> i18n().get(getTitleNew());
        });
    }

    private void switchToEdit() {
        if (session.entity() == null) return;
        session = session.toEdit();
        switchTo();
    }
}
