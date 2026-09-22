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
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.CategoryFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.CategoryViewOverlayModeHandler;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class CategoryOverlay extends AbstractTaxonOverlay<CategoryFormOverlayModeHandler> {

    @Getter private final EntityOverlaySupport support;
    private final UiComponentFactory<CategoryViewOverlayModeHandler, CategoryViewOverlayModeHandler.Parameters> viewModeHandlerFactory;
    private final UiComponentFactory<CategoryFormOverlayModeHandler, CategoryFormOverlayModeHandler.Parameters> formModeHandlerFactory;
    @Getter private final TaxonCatalogService taxonCatalogService;

    @Override protected String  getOverlayCssClass() { return "category-overlay"; }
    @Override protected I18nKey getTitleEdit()        { return CATEGORY_OVERLAY_TITLE_EDIT; }
    @Override protected I18nKey getTitleNew()         { return CATEGORY_OVERLAY_TITLE_NEW; }

    @Override
    protected SaveConfig saveConfig() {
        return new SaveConfig(
                CATEGORY_OVERLAY_NOTIFICATION_SUCCESS,
                CATEGORY_OVERLAY_NOTIFICATION_VALIDATION_FAILED,
                CATEGORY_OVERLAY_NOTIFICATION_SAVE_ERROR,
                CATEGORY_OVERLAY_NOTIFICATION_CONFLICT);
    }

    @Override
    protected Long getSavedEntityId() {
        return currentFormHandler.getSavedTaxonId();
    }

    @Override
    protected OverlayModeHandler buildViewHandler(TaxonDto entity, Runnable onEdit, Runnable onClose) {
        return viewModeHandlerFactory.build(CategoryViewOverlayModeHandler.Parameters.builder()
                .taxon(entity).onEdit(onEdit).onClose(onClose).build());
    }

    @Override
    protected CategoryFormOverlayModeHandler buildFormHandler(TaxonDto entity, Mode mode, List<BreadcrumbStep> breadcrumbSteps) {
        CategoryFormOverlayModeHandler.Mode handlerMode = mode == Mode.CREATE
                ? CategoryFormOverlayModeHandler.Mode.CREATE
                : CategoryFormOverlayModeHandler.Mode.EDIT;
        return formModeHandlerFactory.build(CategoryFormOverlayModeHandler.Parameters.builder()
                .taxon(entity)
                .mode(handlerMode)
                .onSave(this::handleSave)
                .onCancel(this::handleCancel)
                .breadcrumbSteps(breadcrumbSteps)
                .build());
    }
}
