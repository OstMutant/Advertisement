package org.ost.marketplace.ui.views.main.tabs.referencedata;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.CategoryOverlay;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.model.TaxonType;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** The Categories admin screen -- {@link AbstractTaxonManagementView} supplies all the actual list/row/delete/restore logic. */
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class CategoryManagementView extends AbstractTaxonManagementView {

    private static final Labels LABELS = new Labels(
            CATEGORY_VIEW_EMPTY, CATEGORY_VIEW_DELETED_LABEL,
            CATEGORY_VIEW_TOOLTIP_RESTORE, CATEGORY_VIEW_TOOLTIP_EDIT, CATEGORY_VIEW_TOOLTIP_DELETE,
            CATEGORY_VIEW_CONFIRM_DELETE_TITLE, CATEGORY_VIEW_CONFIRM_DELETE_TEXT, CATEGORY_VIEW_CONFIRM_DELETE_BUTTON, CATEGORY_VIEW_CONFIRM_CANCEL_BUTTON,
            CATEGORY_VIEW_NOTIFICATION_DELETE_ERROR, CATEGORY_VIEW_NOTIFICATION_DELETED, CATEGORY_VIEW_NOTIFICATION_RESTORED);

    private final TaxonCatalogService  taxonCatalogService;
    private final EntityOverlaySupport support;
    private final AccessEvaluator      access;
    private final CategoryOverlay      overlay;

    @Override protected TaxonCatalogService  getTaxonCatalogService() { return taxonCatalogService; }
    @Override protected EntityOverlaySupport getSupport()             { return support; }
    @Override protected AccessEvaluator      getAccess()              { return access; }
    @Override protected CategoryOverlay      getOverlay()             { return overlay; }
    @Override protected TaxonType            getTaxonType()           { return TaxonType.CATEGORY; }
    @Override protected String               getCssPrefix()           { return "category"; }
    @Override protected Labels               getLabels()              { return LABELS; }
}
