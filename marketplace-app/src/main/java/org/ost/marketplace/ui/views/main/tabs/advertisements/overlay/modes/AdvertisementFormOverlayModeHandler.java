package org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes;

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
import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.audit.AdvertisementSnapshotDto;
import org.ost.marketplace.entities.Advertisement;
import org.ost.marketplace.security.AccessEvaluator;
import org.ost.marketplace.services.AdvertisementService;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.audit.spi.AuditUiPort;
import org.ost.platform.core.i18n.I18nService;
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
import org.ost.platform.attachment.spi.AttachmentGalleryPort;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.elements.OverlayAdvertisementMetaPanel;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.ui.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.UUID;


import static org.ost.marketplace.common.I18nKey.*;
import static org.ost.marketplace.common.I18nKey.FORM_DISCARD_CHANGES;
import static org.ost.marketplace.common.I18nKey.FORM_RESTORE_BANNER;

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

    private final AdvertisementService                                   advertisementService;
    private final AdvertisementMapper                                    mapper;
    private final AccessEvaluator                                        access;
    @Getter
    private final I18nService                                            i18nService;
    private final NotificationService                                    notificationService;
    private final transient ComponentFactory<AttachmentGalleryPort>      galleryPortFactory;
    private final transient ComponentFactory<OverlayFormBinder>          formBinderFactory;
    private final transient ComponentFactory<AuditPort>                  auditPortFactory;
    private final transient ComponentFactory<AuditUiPort>                auditUiPortFactory;
    private final transient ComponentFactory<UiIconButton>               cancelButtonFactory;
    private final OverlayAdvertisementMetaPanel                          metaPanel;
    private final UiTextField                                            titleField;
    private final UiTextArea                                             descriptionField;
    private final UiPrimaryButton                                        saveButton;
    private final UiTertiaryButton                                       discardButton;

    private Parameters params;
    private boolean    isCreate;
    @Getter
    private Advertisement      savedAdvertisement;
    private AdvertisementInfoDto savedInfoDto;
    private AttachmentGalleryPort.FormHandle activeHandle;
    private Tabs                             formTabs;
    private Tab                              editTab;
    private Div                              activityContent;

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

        AdvertisementEditDto dto = isCreate
                ? new AdvertisementEditDto()
                : mapper.toAdvertisementEdit(params.getAd());
        buildBinder(dto);
        titleField.setValueChangeMode(ValueChangeMode.EAGER);
        descriptionField.setValueChangeMode(ValueChangeMode.EAGER);
        titleField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        descriptionField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));

        Div cardHeader = new Div(VaadinIcon.FORM.create(), new Span(getValue(ADVERTISEMENT_OVERLAY_SECTION_BASIC)));
        cardHeader.addClassName("overlay__form-card-header");

        Div fieldsCard = new Div(cardHeader, titleField, descriptionField);
        fieldsCard.addClassName("overlay__form-fields-card");

        Div content = new Div(fieldsCard);
        galleryPortFactory.ifAvailable(ext -> {
            this.activeHandle = isCreate
                    ? ext.buildGalleryForCreate(EntityType.ADVERTISEMENT, UUID.randomUUID().toString())
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

        if (!isCreate) {
            discardButton.configure(UiTertiaryButton.Parameters.builder()
                    .labelKey(FORM_DISCARD_CHANGES)
                    .build());
            discardButton.addClickListener(_ -> discardChanges());
            layout.setHeaderActions(new Div(saveButton, discardButton, closeBtn));
        } else {
            layout.setHeaderActions(new Div(saveButton, closeBtn));
        }

        updateButtons(false);

        Div tabbedContent = isCreate ? content : buildTabbedContent(content);
        layout.setContent(tabbedContent);
    }

    public boolean save() {
        return binder.save(dto -> {
            this.savedAdvertisement = advertisementService.save(mapper.toAdvertisement(dto));
            advertisementService.findById(savedAdvertisement.getId()).ifPresent(info -> this.savedInfoDto = info);
            if (this.activeHandle != null) {
                this.activeHandle.commit(new EntityRef(EntityType.ADVERTISEMENT, savedAdvertisement.getId()));
            }
        });
    }

    public AdvertisementInfoDto getSavedInfoDto() { return savedInfoDto; }

    public void discard() {
        if (this.activeHandle != null) {
            this.activeHandle.discard();
        }
    }

    public void loadRestored(@NonNull AdvertisementEditDto restoredDto) {
        binder.loadRestored(restoredDto, (src, tgt) -> {
            tgt.setTitle(src.getTitle());
            tgt.setDescription(src.getDescription());
        });
        notificationService.success(FORM_RESTORE_BANNER);
        updateButtons(true);
        if (formTabs != null) formTabs.setSelectedTab(editTab);
    }

    private Div buildTabbedContent(Div editContent) {
        return auditUiPortFactory.findIfAvailable()
                .filter(_ -> access.canOperate(params.getAd()))
                .map(auditUi -> {
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
                            activityContent.add(buildActivityContent(auditUi));
                        }
                    });

                    return new Div(formTabs, editContent, activityContent);
                })
                .orElse(editContent);
    }

    private com.vaadin.flow.component.Component buildActivityContent(AuditUiPort auditUi) {
        return auditUi.buildAuditActivityPanel(AuditUiPort.EntityActivityParams.builder()
                .entityType(EntityType.ADVERTISEMENT)
                .entityId(params.getAd().getId())
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .canOperate(access.canOperate(params.getAd()))
                .onRestoreRequested((item, entityId) -> handleRestoreFromActivity(item.snapshotId()))
                .build());
    }

    private void handleRestoreFromActivity(Long snapshotId) {
        auditPortFactory.ifAvailable(port ->
                port.<AdvertisementSnapshotDto>getSnapshotContent(snapshotId, EntityType.ADVERTISEMENT)
                        .map(content -> content.snapshotData())
                        .ifPresent(snapshot -> {
                            AdvertisementEditDto dto = mapper.toAdvertisementEdit(params.getAd());
                            dto.setTitle(snapshot.title());
                            dto.setDescription(snapshot.description());
                            loadRestored(dto);
                        })
        );
    }

    public void discardChanges() {
        if (params.getAd() == null) return;
        advertisementService.findById(params.getAd().getId()).ifPresent(freshAd -> {
            AdvertisementEditDto fresh = mapper.toAdvertisementEdit(freshAd);
            binder.reload(fresh, (src, tgt) -> {
                tgt.setTitle(src.getTitle());
                tgt.setDescription(src.getDescription());
            });
            updateButtons(false);
        });
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
        if (!isCreate) discardButton.setEnabled(hasChanges);
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
        binder.readInitialValues();
    }
}
