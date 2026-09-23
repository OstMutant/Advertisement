package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes;

import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.dto.CategoryEditDto;
import org.ost.marketplace.ui.views.components.audit.EntityActivityOverlay;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.taxon.model.TaxonType;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class CategoryFormOverlayModeHandler extends AbstractTaxonFormOverlayModeHandler<CategoryEditDto>
        implements Configurable<CategoryFormOverlayModeHandler, AbstractTaxonFormOverlayModeHandler.Parameters> {

    private static final Labels LABELS = new Labels(
            CATEGORY_OVERLAY_SECTION_LABEL, CATEGORY_OVERLAY_BUTTON_SAVE, CATEGORY_OVERLAY_BUTTON_CANCEL,
            CATEGORY_OVERLAY_TITLE_EDIT, CATEGORY_ACTIVITY_BUTTON);

    @Getter
    private final I18nService                                                              i18nService;
    @Getter private final AccessEvaluator                                                  access;
    @Getter private final TaxonCatalogService                                              taxonCatalogService;
    @Getter private final AuditQueryService                                                auditQueryService;
    @Getter private final NotificationService                                              notificationService;
    @Getter private final UiComponentFactory<OverlayFormBinder<CategoryEditDto>, OverlayFormBinder.Parameters<CategoryEditDto>> formBinderFactory;
    @Getter private final EntityActivityOverlay                                            entityActivityOverlay;

    @Override
    public CategoryFormOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override protected TaxonType getTaxonType() { return TaxonType.CATEGORY; }
    @Override protected Class<CategoryEditDto> getDtoClass() { return CategoryEditDto.class; }
    @Override protected CategoryEditDto newDto() { return new CategoryEditDto(); }
    @Override protected String getCssPrefix() { return "category"; }
    @Override protected Labels getLabels() { return LABELS; }

    @Override
    protected LocaleTranslationForm<CategoryEditDto> buildLocaleForm() {
        return new LocaleTranslationForm<>(
                CATEGORY_OVERLAY_FIELD_NAME.toTestId(), CATEGORY_OVERLAY_FIELD_DESCRIPTION.toTestId(),
                new LocaleTranslationForm.Labels(
                        getValue(CATEGORY_OVERLAY_FIELD_NAME), getValue(CATEGORY_OVERLAY_FIELD_NAME_PLACEHOLDER),
                        getValue(CATEGORY_OVERLAY_FIELD_DESCRIPTION), getValue(CATEGORY_OVERLAY_FIELD_DESCRIPTION_PLACEHOLDER),
                        getValue(CATEGORY_OVERLAY_VALIDATION_NAME_REQUIRED), getValue(CATEGORY_OVERLAY_VALIDATION_NAME_LENGTH),
                        getValue(CATEGORY_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED), getValue(CATEGORY_OVERLAY_VALIDATION_DESCRIPTION_LENGTH),
                        getValue(CATEGORY_OVERLAY_LOCALE_TAB_EN), getValue(CATEGORY_OVERLAY_LOCALE_TAB_UK)),
                new LocaleTranslationForm.Accessors<CategoryEditDto>(
                        CategoryEditDto::getNameEn, CategoryEditDto::setNameEn,
                        CategoryEditDto::getDescriptionEn, CategoryEditDto::setDescriptionEn,
                        TaxonSnapshotDto::nameEn, TaxonSnapshotDto::descriptionEn),
                new LocaleTranslationForm.Accessors<CategoryEditDto>(
                        CategoryEditDto::getNameUk, CategoryEditDto::setNameUk,
                        CategoryEditDto::getDescriptionUk, CategoryEditDto::setDescriptionUk,
                        TaxonSnapshotDto::nameUk, TaxonSnapshotDto::descriptionUk));
    }
}
