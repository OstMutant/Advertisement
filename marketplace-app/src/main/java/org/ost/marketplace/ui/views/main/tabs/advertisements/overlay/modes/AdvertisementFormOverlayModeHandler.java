package org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jsoup.Jsoup;
import org.ost.orchestrator.services.AdvertisementDisplayEnrichmentService;
import org.ost.orchestrator.services.AdvertisementReadService;
import org.ost.orchestrator.services.AttachmentMediaService;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.components.audit.EntityActivityOverlay;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.ui.dto.AdvertisementEditDto;
import org.ost.marketplace.ui.mappers.AdvertisementMapper;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.fields.QuillEditor;
import org.ost.marketplace.ui.views.components.fields.UiTextField;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.utils.BeforeUnloadUtil;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGalleryService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.elements.OverlayAdvertisementMetaPanel;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.orchestrator.services.AdvertisementSaveService;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@Uses(Upload.class)
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AdvertisementFormOverlayModeHandler extends AbstractFormOverlayModeHandler<AdvertisementEditDto>
        implements Configurable<AdvertisementFormOverlayModeHandler, AdvertisementFormOverlayModeHandler.Parameters>, I18nParams {

    private static final String DATA_TESTID = "data-testid";

    @Value
    @lombok.Builder
    public static class Parameters {
        AdvertisementInfoDto        ad;
        @NonNull Runnable           onSave;
        @NonNull Runnable           onCancel;
        @NonNull List<BreadcrumbStep> breadcrumbSteps;
    }

    private final AdvertisementReadService                                     advertisementReadService;
    private final AdvertisementSaveService                                     advertisementSaveService;
    private final AdvertisementMapper                                          mapper;
    private final AccessEvaluator                                              access;
    @Getter
    private final I18nService                                                  i18nService;
    private final NotificationService                                          notificationService;
    private final AttachmentMediaService                                       attachmentMediaService;
    private final ComponentFactory<AttachmentGalleryService>                 galleryServiceFactory;
    private final UiComponentFactory<OverlayFormBinder<AdvertisementEditDto>>  formBinderFactory;
    private final AuditQueryService                                             auditQueryService;
    private final EntityActivityOverlay                                        entityActivityOverlay;
    private final OverlayAdvertisementMetaPanel                                metaPanel;
    private final TaxonCatalogService                                          taxonCatalogService;
    private final LocaleProvider                                               localeProvider;
    private final AdvertisementDisplayEnrichmentService                        enrichmentService;

    private QuillEditor descriptionField;
    private UiTextField titleField;

    private Parameters                        params;
    @Getter
    private Long                              savedId;
    @Getter
    private AdvertisementInfoDto              savedInfoDto;
    private AttachmentGalleryService.FormHandle activeHandle;
    private MultiSelectComboBox<TaxonDto>     categoryComboBox;
    private ComboBox<TaxonDto>                cityComboBox;
    private RadioButtonGroup<AdKind>           adKindField;
    private UiPrimaryButton                   saveButton;
    private UiTertiaryButton                  discardButton;

    @Override
    public AdvertisementFormOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        boolean isCreate = params.getAd() == null;

        titleField = new UiTextField(getValue(ADVERTISEMENT_OVERLAY_FIELD_TITLE), getValue(ADVERTISEMENT_OVERLAY_FIELD_TITLE),
                255, true, ADVERTISEMENT_OVERLAY_FIELD_TITLE.toTestId());

        descriptionField = new QuillEditor();
        descriptionField.setLabel(getValue(ADVERTISEMENT_OVERLAY_FIELD_DESCRIPTION));
        descriptionField.setMaxLength(AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH);
        descriptionField.addClassName("overlay__description-rich-editor");
        descriptionField.getElement().setAttribute(DATA_TESTID, "advertisement-overlay-field-description");

        List<TaxonDto> availableCategories = taxonCatalogService.getAllByType(TaxonType.CATEGORY, localeProvider.getCurrentLocale());
        if (!availableCategories.isEmpty()) {
            categoryComboBox = new MultiSelectComboBox<>();
            categoryComboBox.setLabel(getValue(ADVERTISEMENT_OVERLAY_FIELD_CATEGORIES));
            categoryComboBox.setItemLabelGenerator(TaxonDto::getName);
            categoryComboBox.setItems(availableCategories);
            categoryComboBox.getElement().setProperty("maxSelectedItemsCount", 10);
            categoryComboBox.getElement().setAttribute(DATA_TESTID, "advertisement-overlay-field-categories");
        }

        List<TaxonDto> availableCities = taxonCatalogService.getAllByType(TaxonType.CITY, localeProvider.getCurrentLocale());
        if (!availableCities.isEmpty()) {
            cityComboBox = new ComboBox<>();
            cityComboBox.setLabel(getValue(ADVERTISEMENT_OVERLAY_FIELD_CITY));
            cityComboBox.setItemLabelGenerator(TaxonDto::getName);
            cityComboBox.setItems(availableCities);
            cityComboBox.setClearButtonVisible(true);
            cityComboBox.getElement().setAttribute(DATA_TESTID, "advertisement-overlay-field-city");
        }

        adKindField = new RadioButtonGroup<>();
        adKindField.setLabel(getValue(ADVERTISEMENT_OVERLAY_FIELD_AD_KIND));
        adKindField.setItems(AdKind.values());
        adKindField.setItemLabelGenerator(t -> getValue(forAdKind(t)));
        adKindField.getElement().setAttribute(DATA_TESTID, "advertisement-overlay-field-ad-kind");

        AdvertisementEditDto dto = isCreate
                ? AdvertisementEditDto.builder().adKind(AdKind.OFFER).build()
                : mapper.toAdvertisementEdit(params.getAd());
        buildBinder(dto, availableCategories, availableCities);

        titleField.setValueChangeMode(ValueChangeMode.EAGER);
        titleField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        descriptionField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        adKindField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        if (categoryComboBox != null) {
            categoryComboBox.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        }
        if (cityComboBox != null) {
            cityComboBox.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        }

        Div cardHeader = new Div(VaadinIcon.FORM.create(), new Span(getValue(ADVERTISEMENT_OVERLAY_SECTION_BASIC)));
        cardHeader.addClassName("overlay__form-card-header");

        Div fieldsCard = new Div(cardHeader, titleField, descriptionField);
        if (categoryComboBox != null) {
            fieldsCard.add(categoryComboBox);
        }
        if (cityComboBox != null) {
            fieldsCard.add(cityComboBox);
        }
        fieldsCard.add(adKindField);
        fieldsCard.addClassName("overlay__form-fields-card");

        Div content = new Div(fieldsCard);
        if (attachmentMediaService.isAvailable()) {
            AttachmentGalleryService ext = galleryServiceFactory.get();
            this.activeHandle = isCreate
                    ? ext.buildGalleryForCreate(EntityType.ADVERTISEMENT, java.util.UUID.randomUUID().toString())
                    : ext.buildGalleryForEdit(new EntityRef(EntityType.ADVERTISEMENT, params.getAd().getId()));
            activeHandle.setOnChangedListener(() -> updateButtons(true));
            content.add(activeHandle.getComponent());
        }

        if (!isCreate) {
            content.add(metaPanel.configure(OverlayAdvertisementMetaPanel.Parameters.from(params.getAd())));
        }

        saveButton = new UiPrimaryButton(getValue(ADVERTISEMENT_OVERLAY_BUTTON_SAVE));
        UiIconButton closeBtn = new UiIconButton(getValue(ADVERTISEMENT_OVERLAY_BUTTON_CANCEL), VaadinIcon.CLOSE.create());

        wireSaveGuard(saveButton, params.getOnSave());
        closeBtn.addClickListener(_ -> params.getOnCancel().run());

        discardButton = new UiTertiaryButton(getValue(FORM_DISCARD_CHANGES));
        discardButton.addClickListener(_ -> discardChanges());

        Div headerActions = new Div(saveButton, discardButton);
        boolean canOperate = !isCreate && access.canOperate(params.getAd().getOwnerUserId());
        if (canOperate && auditQueryService.isAvailable()) {
            headerActions.add(buildHistoryButton());
        }
        headerActions.add(closeBtn);
        layout.setHeaderActions(headerActions);

        updateButtons(false);
        layout.setContent(content);
    }

    private UiIconButton buildHistoryButton() {
        UiIconButton historyBtn = new UiIconButton(getValue(ADVERTISEMENT_ACTIVITY_BUTTON), VaadinIcon.CLOCK.create());
        historyBtn.addClassName("advertisement-history-button");
        historyBtn.addClickListener(_ -> entityActivityOverlay.openFor(EntityActivityOverlay.Parameters.builder()
                .entityRef(new EntityRef(EntityType.ADVERTISEMENT, params.getAd().getId()))
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .canOperate(access.canOperate(params.getAd().getOwnerUserId()))
                .parentSteps(params.getBreadcrumbSteps())
                .parentFormLabel(getValue(ADVERTISEMENT_OVERLAY_TITLE_EDIT))
                .currentLabelKey(ADVERTISEMENT_ACTIVITY_BUTTON)
                .onRestoreRequested(this::handleRestoreFromActivity)
                .build()));
        return historyBtn;
    }

    public boolean save() {
        return binder.save(dto -> {
            this.savedId = advertisementSaveService.isAvailable()
                    ? advertisementSaveService.save(
                            new AdvertisementSaveDto(dto.getId(), dto.getTitle(), dto.getDescription(), dto.getAdKind(), dto.getCategoryIds(), dto.getCityTaxonId(), dto.getVersion()),
                            access.getCurrentUserId(),
                            this::commitGallery)
                    : null;
            if (savedId != null) {
                advertisementReadService.findById(savedId)
                        .map(ad -> enrichmentService.enrichSingle(ad, localeProvider.getCurrentLocale()))
                        .ifPresent(info -> {
                            this.savedInfoDto = info;
                            dto.setVersion(info.getVersion());
                        });
            }
        });
    }

    private Long commitGallery(EntityRef entityRef) {
        return activeHandle != null ? activeHandle.commit(entityRef) : null;
    }

    public void loadRestored(@NonNull AdvertisementEditDto restoredDto) {
        binder.loadRestored(restoredDto, this::copyEditFields);
        notificationService.success(FORM_RESTORE_BANNER);
        updateButtons(true);
    }

    private void handleRestoreFromActivity(Long snapshotId) {
        auditQueryService.getSnapshotContent(snapshotId, EntityType.ADVERTISEMENT, AdvertisementSnapshotDto.class)
                .ifPresent(content -> {
                    AdvertisementSnapshotDto snapshot = content.snapshotData();
                    AdvertisementEditDto dto = mapper.toAdvertisementEdit(params.getAd());
                    dto.setTitle(snapshot.title());
                    dto.setDescription(snapshot.description());
                    dto.setAdKind(snapshot.adKind());
                    dto.setCategoryIds(snapshot.categoryIds() != null
                            ? new java.util.HashSet<>(snapshot.categoryIds()) : new java.util.HashSet<>());
                    dto.setCityTaxonId(snapshot.cityTaxonId());
                    loadRestored(dto);
                    if (activeHandle != null) activeHandle.loadFromSnapshotId(snapshot.attachmentSnapshotId());
                });
    }

    public void discardChanges() {
        if (params.getAd() == null) {
            binder.reload(AdvertisementEditDto.builder().adKind(AdKind.OFFER).build(), this::copyEditFields);
            updateButtons(false);
            if (activeHandle != null) activeHandle.discard();
            return;
        }
        advertisementReadService.findById(params.getAd().getId())
                .map(ad -> enrichmentService.enrichSingle(ad, localeProvider.getCurrentLocale()))
                .ifPresent(freshAd -> {
                    AdvertisementEditDto fresh = mapper.toAdvertisementEdit(freshAd);
                    binder.reload(fresh, this::copyEditFields);
                    updateButtons(false);
                });
        if (activeHandle != null) activeHandle.discard();
    }

    private void copyEditFields(AdvertisementEditDto src, AdvertisementEditDto tgt) {
        tgt.setTitle(src.getTitle());
        tgt.setDescription(src.getDescription());
        tgt.setAdKind(src.getAdKind());
        tgt.setCategoryIds(src.getCategoryIds());
        tgt.setCityTaxonId(src.getCityTaxonId());
    }

    public void afterSave(boolean success) {
        updateButtons(!success);
    }

    private void updateButtons(boolean hasChanges) {
        saveButton.setEnabled(hasChanges);
        discardButton.setEnabled(hasChanges);
        BeforeUnloadUtil.sync(hasChanges);
    }


    private void buildBinder(AdvertisementEditDto dto, List<TaxonDto> availableCategories, List<TaxonDto> availableCities) {
        binder = formBinderFactory.build(
                OverlayFormBinder.Parameters.<AdvertisementEditDto>builder()
                        .clazz(AdvertisementEditDto.class)
                        .dto(dto)
                        .build()
        );
        binder.getBinder().forField(titleField)
                .asRequired(getValue(ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_REQUIRED))
                .withValidator(new StringLengthValidator(
                        getValue(ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_LENGTH), 1, 255))
                .bind(AdvertisementEditDto::getTitle, AdvertisementEditDto::setTitle);
        binder.getBinder().forField(descriptionField)
                .asRequired(getValue(ADVERTISEMENT_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED))
                .withValidator(
                        html -> html != null
                                && Jsoup.parse(html).text().length() <= AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH,
                        getValue(ADVERTISEMENT_OVERLAY_VALIDATION_DESCRIPTION_LENGTH)
                )
                .bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);
        binder.getBinder().forField(adKindField)
                .asRequired(getValue(ADVERTISEMENT_OVERLAY_VALIDATION_AD_KIND_REQUIRED))
                .bind(AdvertisementEditDto::getAdKind, AdvertisementEditDto::setAdKind);
        if (categoryComboBox != null) {
            binder.getBinder().forField(categoryComboBox)
                    .withConverter(
                            selected -> selected.stream().map(TaxonDto::getId).collect(Collectors.toSet()),
                            ids -> ids == null ? Set.of() : availableCategories.stream()
                                    .filter(t -> ids.contains(t.getId()))
                                    .collect(Collectors.toSet())
                    )
                    .bind(AdvertisementEditDto::getCategoryIds, AdvertisementEditDto::setCategoryIds);
        }
        if (cityComboBox != null) {
            binder.getBinder().forField(cityComboBox)
                    .withConverter(
                            selected -> selected == null ? null : selected.getId(),
                            id -> id == null ? null : availableCities.stream()
                                    .filter(t -> t.getId().equals(id))
                                    .findFirst().orElse(null)
                    )
                    .bind(AdvertisementEditDto::getCityTaxonId, AdvertisementEditDto::setCityTaxonId);
        }
        binder.readInitialValues();
    }
}
