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
public class CategoryViewOverlayModeHandler extends AbstractTaxonViewOverlayModeHandler
        implements Configurable<CategoryViewOverlayModeHandler, CategoryViewOverlayModeHandler.Parameters> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull TaxonDto  taxon;
        @NonNull Runnable  onEdit;
        @NonNull Runnable  onClose;
    }

    private static final Labels LABELS = new Labels(
            CATEGORY_OVERLAY_SECTION_LABEL, CATEGORY_OVERLAY_LOCALE_TAB_EN, CATEGORY_OVERLAY_LOCALE_TAB_UK,
            CATEGORY_VIEW_BUTTON_EDIT, CATEGORY_OVERLAY_BUTTON_CANCEL);

    @Getter
    private final I18nService         i18nService;
    private final AccessEvaluator     access;
    @Getter
    private final TaxonCatalogService taxonCatalogService;

    private Parameters params;

    @Override
    public CategoryViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override protected AccessEvaluator getAccess()   { return access; }
    @Override protected Long            getEntityId() { return params.getTaxon().getId(); }
    @Override protected Runnable        getOnEdit()   { return params.getOnEdit(); }
    @Override protected Runnable        getOnClose()  { return params.getOnClose(); }
    @Override protected Labels          getLabels()   { return LABELS; }
}
