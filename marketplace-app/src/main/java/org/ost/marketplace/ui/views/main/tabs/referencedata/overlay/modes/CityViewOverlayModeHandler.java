package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes;

import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class CityViewOverlayModeHandler extends AbstractTaxonViewOverlayModeHandler
        implements Configurable<CityViewOverlayModeHandler, CityViewOverlayModeHandler.Parameters> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull TaxonDto  city;
        @NonNull Runnable  onEdit;
        @NonNull Runnable  onClose;
    }

    private static final Labels LABELS = new Labels(
            CITY_OVERLAY_SECTION_LABEL, CITY_OVERLAY_LOCALE_TAB_EN, CITY_OVERLAY_LOCALE_TAB_UK,
            CITY_VIEW_BUTTON_EDIT, CITY_OVERLAY_BUTTON_CANCEL);

    @Getter
    private final I18nService         i18nService;
    private final AccessEvaluator     access;
    @Getter
    private final TaxonCatalogService taxonCatalogService;

    private Parameters params;

    @Override
    public CityViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override protected AccessEvaluator getAccess()   { return access; }
    @Override protected Long            getEntityId() { return params.getCity().getId(); }
    @Override protected Runnable        getOnEdit()   { return params.getOnEdit(); }
    @Override protected Runnable        getOnClose()  { return params.getOnClose(); }
    @Override protected Labels          getLabels()   { return LABELS; }
}
