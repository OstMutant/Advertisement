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
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.CityFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.CityViewOverlayModeHandler;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.spi.TaxonPort;

import java.util.Locale;
import java.util.function.Consumer;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class CityOverlay extends AbstractEntityOverlay<CityFormOverlayModeHandler> {

    private enum Mode { VIEW, CREATE, EDIT }

    private record OverlaySession(
            @NonNull Mode               mode,
            TaxonDto                    city,
            @NonNull Consumer<TaxonDto> onUpdated,
            @NonNull Runnable           onListChanged,
            boolean                     enteredFromView
    ) {
        OverlaySession toEdit()                 { return new OverlaySession(Mode.EDIT, city, onUpdated, onListChanged, true); }
        OverlaySession toView()                 { return new OverlaySession(Mode.VIEW, city, onUpdated, onListChanged, false); }
        OverlaySession withCity(TaxonDto fresh) { return new OverlaySession(mode, fresh, onUpdated, onListChanged, enteredFromView); }
    }

    @Getter private final EntityOverlaySupport support;
    private final UiComponentFactory<CityViewOverlayModeHandler> viewModeHandlerFactory;
    private final UiComponentFactory<CityFormOverlayModeHandler> formModeHandlerFactory;
    private final ComponentFactory<TaxonPort>                    taxonPortFactory;

    private OverlaySession session;

    @Override protected String  getOverlayCssClass()   { return "city-overlay"; }
    @Override protected I18nKey getBreadcrumbLabelKey() { return MAIN_TAB_REFERENCE_DATA; }

    @Override
    protected SaveConfig saveConfig() {
        return new SaveConfig(
                CITY_OVERLAY_NOTIFICATION_SUCCESS,
                CITY_OVERLAY_NOTIFICATION_VALIDATION_FAILED,
                CITY_OVERLAY_NOTIFICATION_SAVE_ERROR,
                CITY_OVERLAY_NOTIFICATION_CONFLICT);
    }

    @Override
    protected void proceed() {
        Long savedId = currentFormHandler.getSavedCityId();
        TaxonDto fresh = savedId == null ? null : taxonPortFactory.findIfAvailable()
                .flatMap(p -> p.findById(savedId, Locale.ENGLISH))
                .orElse(null);
        if (fresh == null) return;

        session = session.withCity(fresh);
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

    public void openForView(@NonNull TaxonDto city, @NonNull Consumer<TaxonDto> onUpdated) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, city, onUpdated, () -> {}, false));
    }

    public void openForCreate(@NonNull Runnable onListChanged) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.CREATE, null, _ -> {}, onListChanged, false));
    }

    public void openForEdit(@NonNull TaxonDto city, @NonNull Consumer<TaxonDto> onUpdated) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, city, onUpdated, () -> {}, false));
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
                    CityViewOverlayModeHandler.Parameters.builder()
                            .city(session.city())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeToList)
                            .build());
            case CREATE, EDIT -> {
                CityFormOverlayModeHandler.Mode handlerMode = session.mode() == Mode.CREATE
                        ? CityFormOverlayModeHandler.Mode.CREATE
                        : CityFormOverlayModeHandler.Mode.EDIT;
                currentFormHandler = formModeHandlerFactory.build(
                        CityFormOverlayModeHandler.Parameters.builder()
                                .city(session.city())
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
            case EDIT   -> i18n().get(CITY_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n().get(CITY_OVERLAY_TITLE_NEW);
        };
        layout.getBreadcrumbCurrent().setText(breadcrumb);
        layout.getBreadcrumbCurrent().setVisible(!breadcrumb.isEmpty());
    }

    private void switchToEdit() {
        if (session.city() == null) return;
        session = session.toEdit();
        switchTo();
    }
}
