package org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes;

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.marketplace.ui.views.components.audit.AuditActivityPanel;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.ui.dto.AdvertisementEditDto;
import org.ost.marketplace.ui.mappers.AdvertisementMapper;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.fields.UiTextArea;
import org.ost.marketplace.ui.views.components.fields.UiTextField;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGalleryService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.elements.OverlayAdvertisementMetaPanel;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;
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

    @Value
    @lombok.Builder
    public static class Parameters {
        AdvertisementInfoDto ad;
        @NonNull Runnable    onSave;
        @NonNull Runnable    onCancel;
    }

    private final ComponentFactory<AdvertisementPort>                          advertisementPortFactory;
    private final AdvertisementMapper                                          mapper;
    private final AccessEvaluator                                              access;
    @Getter
    private final I18nService                                                  i18nService;
    private final NotificationService                                          notificationService;
    private final UiComponentFactory<AttachmentGalleryService>                 galleryServiceFactory;
    private final UiComponentFactory<OverlayFormBinder<AdvertisementEditDto>>  formBinderFactory;
    private final ComponentFactory<AuditPort>                                  auditPortFactory;
    private final UiComponentFactory<AuditActivityPanel>                       auditActivityPanelFactory;
    private final UiComponentFactory<UiIconButton>                             cancelButtonFactory;
    private final OverlayAdvertisementMetaPanel                                metaPanel;
    private final UiTextField                                                  titleField;
    private final UiTextArea                                                   descriptionField;
    private final UiPrimaryButton                                              saveButton;
    private final UiTertiaryButton                                             discardButton;
    private final ComponentFactory<TaxonPort>                                  taxonPortFactory;
    private final LocaleProvider                                               localeProvider;

    private Parameters                        params;
    private boolean                           isCreate;
    @Getter
    private Long                              savedId;
    @Getter
    private AdvertisementInfoDto              savedInfoDto;
    private AttachmentGalleryService.FormHandle activeHandle;
    private Tabs                              formTabs;
    private Tab                               editTab;
    private Div                               activityContent;
    private MultiSelectComboBox<TaxonDto>     categoryComboBox;
    private List<TaxonDto>                    availableCategories = List.of();

    @Override
    public AdvertisementFormOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        this.isCreate = params.getAd() == null;

        titleField.configure(UiTextField.Parameters.builder()
                .labelKey(ADVERTISEMENT_OVERLAY_FIELD_TITLE)
                .placeholderKey(ADVERTISEMENT_OVERLAY_FIELD_TITLE)
                .maxLength(255)
                .required(true)
                .build());

        descriptionField.configure(UiTextArea.Parameters.builder()
                .labelKey(ADVERTISEMENT_OVERLAY_FIELD_DESCRIPTION)
                .placeholderKey(ADVERTISEMENT_OVERLAY_FIELD_DESCRIPTION)
                .maxLength(1000)
                .required(true)
                .build());
        descriptionField.addClassName("overlay__description-text-area");

        availableCategories = taxonPortFactory.findIfAvailable()
                .map(p -> p.getAllByType(TaxonType.CATEGORY, localeProvider.getCurrentLocale()))
                .orElse(List.of());
        if (!availableCategories.isEmpty()) {
            categoryComboBox = new MultiSelectComboBox<>();
            categoryComboBox.setLabel(getValue(ADVERTISEMENT_OVERLAY_FIELD_CATEGORIES));
            categoryComboBox.setItemLabelGenerator(TaxonDto::getName);
            categoryComboBox.setItems(availableCategories);
            categoryComboBox.getElement().setProperty("maxSelectedItemsCount", 10);
            categoryComboBox.getElement().setAttribute("data-testid", "advertisement-overlay-field-categories");
        }

        AdvertisementEditDto dto = isCreate
                ? new AdvertisementEditDto()
                : mapper.toAdvertisementEdit(params.getAd());
        buildBinder(dto);

        titleField.setValueChangeMode(ValueChangeMode.EAGER);
        descriptionField.setValueChangeMode(ValueChangeMode.EAGER);
        titleField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        descriptionField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        if (categoryComboBox != null) {
            categoryComboBox.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        }

        Div cardHeader = new Div(VaadinIcon.FORM.create(), new Span(getValue(ADVERTISEMENT_OVERLAY_SECTION_BASIC)));
        cardHeader.addClassName("overlay__form-card-header");

        Div fieldsCard = new Div(cardHeader, titleField, descriptionField);
        if (categoryComboBox != null) {
            fieldsCard.add(categoryComboBox);
        }
        fieldsCard.addClassName("overlay__form-fields-card");

        Div content = new Div(fieldsCard);
        galleryServiceFactory.ifAvailable(ext -> {
            this.activeHandle = isCreate
                    ? ext.buildGalleryForCreate(EntityType.ADVERTISEMENT, java.util.UUID.randomUUID().toString())
                    : ext.buildGalleryForEdit(new EntityRef(EntityType.ADVERTISEMENT, params.getAd().getId()));
            activeHandle.setOnChangedListener(() -> updateButtons(true));
            content.add(activeHandle.getComponent());
        });

        if (!isCreate) {
            content.add(metaPanel.configure(OverlayAdvertisementMetaPanel.Parameters.from(params.getAd())));
        }

        saveButton.configure(UiPrimaryButton.Parameters.builder()
                .labelKey(ADVERTISEMENT_OVERLAY_BUTTON_SAVE)
                .build());
        UiIconButton closeBtn = cancelButtonFactory.build(UiIconButton.Parameters.builder()
                .labelKey(ADVERTISEMENT_OVERLAY_BUTTON_CANCEL)
                .icon(VaadinIcon.CLOSE.create())
                .build());

        wireSaveGuard(saveButton, params.getOnSave());
        closeBtn.addClickListener(_ -> params.getOnCancel().run());

        discardButton.configure(UiTertiaryButton.Parameters.builder()
                .labelKey(FORM_DISCARD_CHANGES)
                .build());
        discardButton.addClickListener(_ -> discardChanges());
        layout.setHeaderActions(new Div(saveButton, discardButton, closeBtn));

        updateButtons(false);

        Div tabbedContent = isCreate ? content : buildTabbedContent(content);
        layout.setContent(tabbedContent);
    }

    public boolean save() {
        return binder.save(dto -> {
            this.savedId = advertisementPortFactory.findIfAvailable()
                    .map(p -> p.save(new AdvertisementSaveDto(dto.getId(), dto.getTitle(), dto.getDescription(), dto.getCategoryIds()), access.getCurrentUserId()))
                    .orElse(null);
            if (savedId != null) {
                advertisementPortFactory.findIfAvailable()
                        .flatMap(p -> p.findById(savedId))
                        .ifPresent(info -> this.savedInfoDto = info);
                if (this.activeHandle != null) {
                    this.activeHandle.commit(new EntityRef(EntityType.ADVERTISEMENT, savedId));
                }
            }
        });
    }

    public void discard() {
        if (this.activeHandle != null) {
            this.activeHandle.discard();
        }
    }

    public void loadRestored(@NonNull AdvertisementEditDto restoredDto) {
        binder.loadRestored(restoredDto, (src, tgt) -> {
            tgt.setTitle(src.getTitle());
            tgt.setDescription(src.getDescription());
            tgt.setCategoryIds(src.getCategoryIds());
        });
        notificationService.success(FORM_RESTORE_BANNER);
        updateButtons(true);
        if (formTabs != null) formTabs.setSelectedTab(editTab);
    }

    private Div buildTabbedContent(Div editContent) {
        return auditActivityPanelFactory.findIfAvailable()
                .filter(_ -> access.canOperate(params.getAd().getOwnerUserId()))
                .map(_ -> {
                    formTabs = new Tabs();
                    formTabs.addClassName("adv-form-tabs");
                    editTab = new Tab(getValue(ADVERTISEMENT_OVERLAY_SECTION_BASIC));
                    Tab activityTab = new Tab(getValue(ADVERTISEMENT_ACTIVITY_TAB));
                    formTabs.add(editTab, activityTab);

                    activityContent = new Div();
                    activityContent.addClassName("entity-activity-content");
                    activityContent.setVisible(false);

                    formTabs.addSelectedChangeListener(event -> {
                        boolean isEdit = event.getSelectedTab() == editTab;
                        editContent.setVisible(isEdit);
                        activityContent.setVisible(!isEdit);
                        if (!isEdit && activityContent.getChildren().findFirst().isEmpty()) {
                            activityContent.add(buildActivityContent());
                        }
                    });

                    return new Div(formTabs, editContent, activityContent);
                })
                .orElse(editContent);
    }

    private com.vaadin.flow.component.Component buildActivityContent() {
        return auditActivityPanelFactory.build(AuditActivityPanel.Parameters.builder()
                .entityRef(new EntityRef(EntityType.ADVERTISEMENT, params.getAd().getId()))
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .canOperate(access.canOperate(params.getAd().getOwnerUserId()))
                .onRestoreRequested(this::handleRestoreFromActivity)
                .build());
    }

    private void handleRestoreFromActivity(Long snapshotId) {
        auditPortFactory.ifAvailable(port ->
                port.<AdvertisementSnapshotDto>getSnapshotContent(snapshotId, EntityType.ADVERTISEMENT)
                        .ifPresent(content -> {
                            AdvertisementSnapshotDto snapshot = content.snapshotData();
                            AdvertisementEditDto dto = mapper.toAdvertisementEdit(params.getAd());
                            dto.setTitle(snapshot.title());
                            dto.setDescription(snapshot.description());
                            loadRestored(dto);
                            if (activeHandle != null) activeHandle.loadFromSnapshot(content.version());
                        })
        );
    }

    public void discardChanges() {
        if (params.getAd() == null) {
            binder.reload(new AdvertisementEditDto(), (src, tgt) -> {
                tgt.setTitle(src.getTitle());
                tgt.setDescription(src.getDescription());
                tgt.setCategoryIds(src.getCategoryIds());
            });
            updateButtons(false);
            if (activeHandle != null) activeHandle.discard();
            return;
        }
        advertisementPortFactory.findIfAvailable()
                .flatMap(p -> p.findById(params.getAd().getId()))
                .ifPresent(freshAd -> {
                    AdvertisementEditDto fresh = mapper.toAdvertisementEdit(freshAd);
                    binder.reload(fresh, (src, tgt) -> {
                        tgt.setTitle(src.getTitle());
                        tgt.setDescription(src.getDescription());
                        tgt.setCategoryIds(src.getCategoryIds());
                    });
                    updateButtons(false);
                });
        if (activeHandle != null) activeHandle.discard();
    }

    public void afterSave(boolean success) {
        if (success) {
            updateButtons(false);
            if (formTabs != null) formTabs.setSelectedTab(editTab);
            if (activityContent != null) activityContent.removeAll();
        } else {
            updateButtons(true);
        }
    }

    private void updateButtons(boolean hasChanges) {
        saveButton.setEnabled(hasChanges);
        discardButton.setEnabled(hasChanges);
    }

    private void buildBinder(AdvertisementEditDto dto) {
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
                .bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);
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
        binder.readInitialValues();
    }
}
