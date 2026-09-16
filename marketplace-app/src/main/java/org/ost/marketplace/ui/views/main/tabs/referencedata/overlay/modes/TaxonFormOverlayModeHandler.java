package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.dto.TaxonEditDto;
import org.ost.marketplace.ui.views.components.audit.EntityActivityOverlay;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.utils.BeforeUnloadUtil;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class TaxonFormOverlayModeHandler extends AbstractFormOverlayModeHandler<TaxonEditDto>
        implements Configurable<TaxonFormOverlayModeHandler, TaxonFormOverlayModeHandler.Parameters>, I18nParams {

    public enum Mode { CREATE, EDIT }

    @Value
    @lombok.Builder
    public static class Parameters {
        TaxonDto  taxon;
        @NonNull Mode     mode;
        @NonNull Runnable onSave;
        @NonNull Runnable onCancel;
        @NonNull List<BreadcrumbStep> breadcrumbSteps;
    }

    @Getter
    private final I18nService                                              i18nService;
    private final AccessEvaluator                                          access;
    private final TaxonCatalogService                                      taxonCatalogService;
    private final AuditQueryService                                         auditQueryService;
    private final NotificationService                                      notificationService;
    private final UiComponentFactory<OverlayFormBinder<TaxonEditDto>, OverlayFormBinder.Parameters<TaxonEditDto>>      formBinderFactory;
    private final EntityActivityOverlay                                    entityActivityOverlay;

    private Parameters params;
    @Getter private Long savedTaxonId;
    private LocaleTranslationForm<TaxonEditDto> localeForm;
    private UiPrimaryButton   saveButton;
    private UiTertiaryButton  discardButton;

    @Override
    public TaxonFormOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        localeForm = new LocaleTranslationForm<>(
                TAXON_OVERLAY_FIELD_NAME.toTestId(), TAXON_OVERLAY_FIELD_DESCRIPTION.toTestId(),
                new LocaleTranslationForm.Labels(
                        getValue(TAXON_OVERLAY_FIELD_NAME), getValue(TAXON_OVERLAY_FIELD_NAME_PLACEHOLDER),
                        getValue(TAXON_OVERLAY_FIELD_DESCRIPTION), getValue(TAXON_OVERLAY_FIELD_DESCRIPTION_PLACEHOLDER),
                        getValue(TAXON_OVERLAY_VALIDATION_NAME_REQUIRED), getValue(TAXON_OVERLAY_VALIDATION_NAME_LENGTH),
                        getValue(TAXON_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED), getValue(TAXON_OVERLAY_VALIDATION_DESCRIPTION_LENGTH),
                        getValue(TAXON_OVERLAY_LOCALE_TAB_EN), getValue(TAXON_OVERLAY_LOCALE_TAB_UK)),
                new LocaleTranslationForm.Accessors<TaxonEditDto>(
                        TaxonEditDto::getNameEn, TaxonEditDto::setNameEn,
                        TaxonEditDto::getDescriptionEn, TaxonEditDto::setDescriptionEn,
                        TaxonSnapshotDto::nameEn, TaxonSnapshotDto::descriptionEn),
                new LocaleTranslationForm.Accessors<TaxonEditDto>(
                        TaxonEditDto::getNameUk, TaxonEditDto::setNameUk,
                        TaxonEditDto::getDescriptionUk, TaxonEditDto::setDescriptionUk,
                        TaxonSnapshotDto::nameUk, TaxonSnapshotDto::descriptionUk));

        saveButton = new UiPrimaryButton(getValue(TAXON_OVERLAY_BUTTON_SAVE));
        discardButton = new UiTertiaryButton(getValue(FORM_DISCARD_CHANGES));
        UiIconButton closeBtn = new UiIconButton(getValue(TAXON_OVERLAY_BUTTON_CANCEL), VaadinIcon.CLOSE.create());

        wireSaveGuard(saveButton, params.getOnSave());
        discardButton.addClickListener(_ -> discardChanges());
        closeBtn.addClickListener(_ -> params.getOnCancel().run());

        TaxonEditDto dto = buildDto();
        buildBinder(dto);
        localeForm.wireValueChangeListeners(() -> updateButtons(binder.hasChanges()));

        Div fieldsCard = localeForm.buildFieldsCard(VaadinIcon.TAG.create(), getValue(TAXON_OVERLAY_SECTION_LABEL));
        Div editContent = new Div(fieldsCard);
        layout.setContent(editContent);

        Div headerActions = new Div(saveButton, discardButton);
        if (params.getMode() != Mode.CREATE && auditQueryService.isAvailable()) {
            headerActions.add(buildHistoryButton());
        }
        headerActions.add(closeBtn);
        layout.setHeaderActions(headerActions);
        updateButtons(false);
    }

    private UiIconButton buildHistoryButton() {
        UiIconButton historyBtn = new UiIconButton(getValue(TAXON_ACTIVITY_BUTTON), VaadinIcon.CLOCK.create());
        historyBtn.addClassName("taxon-history-button");
        historyBtn.addClickListener(_ -> entityActivityOverlay.openFor(EntityActivityOverlay.Parameters.builder()
                .entityRef(new EntityRef(EntityType.TAXON, params.getTaxon().getId()))
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .canOperate(access.isPrivileged())
                .parentSteps(params.getBreadcrumbSteps())
                .parentFormLabel(getValue(TAXON_OVERLAY_TITLE_EDIT))
                .currentLabelKey(TAXON_ACTIVITY_BUTTON)
                .onRestoreRequested(this::handleRestoreFromActivity)
                .build()));
        return historyBtn;
    }

    public boolean save() {
        return binder.save(dto -> {
            if (!taxonCatalogService.isAvailable()) return;
            Map<Locale, TaxonTranslationDto> translations = Map.of(
                    Locale.ENGLISH,    TaxonTranslationDto.builder().locale("en").name(dto.getNameEn()).description(dto.getDescriptionEn()).build(),
                    Locale.forLanguageTag("uk"), TaxonTranslationDto.builder().locale("uk").name(dto.getNameUk()).description(dto.getDescriptionUk()).build()
            );
            if (params.getMode() == Mode.CREATE) {
                savedTaxonId = taxonCatalogService.create(org.ost.platform.taxon.model.TaxonType.CATEGORY, translations, access.getCurrentUserId());
            } else {
                taxonCatalogService.update(params.getTaxon().getId(), translations, access.getCurrentUserId(), params.getTaxon().getVersion());
                savedTaxonId = params.getTaxon().getId();
            }
            taxonCatalogService.findById(savedTaxonId, Locale.ENGLISH).ifPresent(fresh -> params = Parameters.builder()
                    .taxon(fresh)
                    .mode(params.getMode())
                    .onSave(params.getOnSave())
                    .onCancel(params.getOnCancel())
                    .breadcrumbSteps(params.getBreadcrumbSteps())
                    .build());
        });
    }

    public void discardChanges() {
        TaxonEditDto fresh = buildDto();
        binder.reload(fresh, localeForm::copyLocaleFields);
        updateButtons(false);
    }

    public void afterSave(boolean success) {
        updateButtons(!success);
    }

    private void handleRestoreFromActivity(long snapshotId) {
        auditQueryService.getSnapshotContent(snapshotId, EntityType.TAXON, TaxonSnapshotDto.class)
                .ifPresent(content -> {
                    TaxonSnapshotDto snapshot = content.snapshotData();
                    TaxonEditDto dto = new TaxonEditDto();
                    dto.setId(params.getTaxon().getId());
                    localeForm.restoreFromSnapshot(snapshot, dto);
                    loadRestored(dto);
                });
    }

    public void loadRestored(@NonNull TaxonEditDto restoredDto) {
        binder.loadRestored(restoredDto, localeForm::copyLocaleFields);
        notificationService.success(FORM_RESTORE_BANNER);
        updateButtons(true);
    }

    private TaxonEditDto buildDto() {
        TaxonEditDto dto = new TaxonEditDto();
        Long id = params.getTaxon() != null ? params.getTaxon().getId() : savedTaxonId;
        if (id != null) {
            dto.setId(id);
            List<TaxonTranslationDto> translations = taxonCatalogService.getTranslations(id);
            for (TaxonTranslationDto t : translations) {
                if ("en".equals(t.getLocale())) {
                    dto.setNameEn(t.getName());
                    dto.setDescriptionEn(t.getDescription());
                } else if ("uk".equals(t.getLocale())) {
                    dto.setNameUk(t.getName());
                    dto.setDescriptionUk(t.getDescription());
                }
            }
        }
        return dto;
    }

    private void buildBinder(TaxonEditDto dto) {
        binder = formBinderFactory.build(
                OverlayFormBinder.Parameters.<TaxonEditDto>builder()
                        .clazz(TaxonEditDto.class)
                        .dto(dto)
                        .build()
        );
        localeForm.bindValidation(binder);
        binder.readInitialValues();
    }

    private void updateButtons(boolean hasChanges) {
        saveButton.setEnabled(hasChanges);
        discardButton.setEnabled(hasChanges);
        BeforeUnloadUtil.sync(hasChanges);
    }
}
