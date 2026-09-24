package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.AbstractTaxonFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.CityFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.CityViewOverlayModeHandler;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class CityOverlay extends AbstractTaxonOverlay<CityFormOverlayModeHandler> {

    @Getter private final EntityOverlaySupport support;
    private final UiComponentFactory<CityViewOverlayModeHandler, CityViewOverlayModeHandler.Parameters> viewModeHandlerFactory;
    private final UiComponentFactory<CityFormOverlayModeHandler, AbstractTaxonFormOverlayModeHandler.Parameters> formModeHandlerFactory;
    @Getter private final TaxonCatalogService taxonCatalogService;

    @Override protected String  getOverlayCssClass() { return "city-overlay"; }
    @Override protected I18nKey getTitleEdit()        { return CITY_OVERLAY_TITLE_EDIT; }
    @Override protected I18nKey getTitleNew()         { return CITY_OVERLAY_TITLE_NEW; }

    @Override
    protected SaveConfig saveConfig() {
        return new SaveConfig(
                CITY_OVERLAY_NOTIFICATION_SUCCESS,
                CITY_OVERLAY_NOTIFICATION_VALIDATION_FAILED,
                CITY_OVERLAY_NOTIFICATION_SAVE_ERROR,
                CITY_OVERLAY_NOTIFICATION_CONFLICT);
    }

    @Override
    protected OverlayModeHandler buildViewHandler(TaxonDto entity, Runnable onEdit, Runnable onClose) {
        return viewModeHandlerFactory.build(CityViewOverlayModeHandler.Parameters.builder()
                .city(entity).onEdit(onEdit).onClose(onClose).build());
    }

    @Override
    protected CityFormOverlayModeHandler buildFormHandler(TaxonDto entity, Mode mode, List<BreadcrumbStep> breadcrumbSteps) {
        return formModeHandlerFactory.build(new AbstractTaxonFormOverlayModeHandler.Parameters(
                entity, toHandlerMode(mode), this::handleSave, this::handleCancel, breadcrumbSteps));
    }
}
