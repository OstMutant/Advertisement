package org.ost.marketplace.ui.views.main.tabs.referencedata;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.CityOverlay;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.model.TaxonType;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** The Cities admin screen -- {@link AbstractTaxonManagementView} supplies all the actual list/row/delete/restore logic. */
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class CityManagementView extends AbstractTaxonManagementView {

    private static final Labels LABELS = new Labels(
            CITY_VIEW_EMPTY, CITY_VIEW_DELETED_LABEL,
            CITY_VIEW_TOOLTIP_RESTORE, CITY_VIEW_TOOLTIP_EDIT, CITY_VIEW_TOOLTIP_DELETE,
            CITY_VIEW_CONFIRM_DELETE_TITLE, CITY_VIEW_CONFIRM_DELETE_TEXT, CITY_VIEW_CONFIRM_DELETE_BUTTON, CITY_VIEW_CONFIRM_CANCEL_BUTTON,
            CITY_VIEW_NOTIFICATION_DELETE_ERROR, CITY_VIEW_NOTIFICATION_DELETED, CITY_VIEW_NOTIFICATION_RESTORED);

    private final TaxonCatalogService  taxonCatalogService;
    private final EntityOverlaySupport support;
    private final AccessEvaluator      access;
    private final CityOverlay          overlay;

    @Override protected TaxonCatalogService  getTaxonCatalogService() { return taxonCatalogService; }
    @Override protected EntityOverlaySupport getSupport()             { return support; }
    @Override protected AccessEvaluator      getAccess()              { return access; }
    @Override protected CityOverlay          getOverlay()             { return overlay; }
    @Override protected TaxonType            getTaxonType()           { return TaxonType.CITY; }
    @Override protected String               getCssPrefix()           { return "city"; }
    @Override protected Labels               getLabels()              { return LABELS; }
}
