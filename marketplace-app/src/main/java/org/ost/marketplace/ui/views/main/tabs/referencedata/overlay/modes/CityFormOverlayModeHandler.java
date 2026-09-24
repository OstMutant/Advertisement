package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes;

import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.dto.CityEditDto;
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
public class CityFormOverlayModeHandler extends AbstractTaxonFormOverlayModeHandler<CityEditDto>
        implements Configurable<CityFormOverlayModeHandler, AbstractTaxonFormOverlayModeHandler.Parameters> {

    private static final Labels LABELS = new Labels(
            CITY_OVERLAY_SECTION_LABEL, CITY_OVERLAY_BUTTON_SAVE, CITY_OVERLAY_BUTTON_CANCEL,
            CITY_OVERLAY_TITLE_EDIT, CITY_ACTIVITY_BUTTON);

    @Getter
    private final I18nService                                                          i18nService;
    @Getter private final AccessEvaluator                                              access;
    @Getter private final TaxonCatalogService                                          taxonCatalogService;
    @Getter private final AuditQueryService                                            auditQueryService;
    @Getter private final NotificationService                                          notificationService;
    @Getter private final UiComponentFactory<OverlayFormBinder<CityEditDto>, OverlayFormBinder.Parameters<CityEditDto>> formBinderFactory;
    @Getter private final EntityActivityOverlay                                        entityActivityOverlay;

    @Override
    public CityFormOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override protected TaxonType getTaxonType() { return TaxonType.CITY; }
    @Override protected Class<CityEditDto> getDtoClass() { return CityEditDto.class; }
    @Override protected CityEditDto newDto() { return new CityEditDto(); }
    @Override protected String getCssPrefix() { return "city"; }
    @Override protected Labels getLabels() { return LABELS; }

    @Override
    protected LocaleTranslationForm<CityEditDto> buildLocaleForm() {
        return new LocaleTranslationForm<>(
                CITY_OVERLAY_FIELD_NAME.toTestId(), CITY_OVERLAY_FIELD_DESCRIPTION.toTestId(),
                new LocaleTranslationForm.Labels(
                        getValue(CITY_OVERLAY_FIELD_NAME), getValue(CITY_OVERLAY_FIELD_NAME_PLACEHOLDER),
                        getValue(CITY_OVERLAY_FIELD_DESCRIPTION), getValue(CITY_OVERLAY_FIELD_DESCRIPTION_PLACEHOLDER),
                        getValue(CITY_OVERLAY_VALIDATION_NAME_REQUIRED), getValue(CITY_OVERLAY_VALIDATION_NAME_LENGTH),
                        getValue(CITY_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED), getValue(CITY_OVERLAY_VALIDATION_DESCRIPTION_LENGTH),
                        getValue(CITY_OVERLAY_LOCALE_TAB_EN), getValue(CITY_OVERLAY_LOCALE_TAB_UK)),
                new LocaleTranslationForm.Accessors<CityEditDto>(
                        CityEditDto::getNameEn, CityEditDto::setNameEn,
                        CityEditDto::getDescriptionEn, CityEditDto::setDescriptionEn,
                        TaxonSnapshotDto::nameEn, TaxonSnapshotDto::descriptionEn),
                new LocaleTranslationForm.Accessors<CityEditDto>(
                        CityEditDto::getNameUk, CityEditDto::setNameUk,
                        CityEditDto::getDescriptionUk, CityEditDto::setDescriptionUk,
                        TaxonSnapshotDto::nameUk, TaxonSnapshotDto::descriptionUk));
    }
}
