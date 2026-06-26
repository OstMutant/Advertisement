package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.data.value.ValueChangeMode;
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
import org.ost.marketplace.ui.views.components.audit.AuditActivityPanel;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.fields.UiTextArea;
import org.ost.marketplace.ui.views.components.fields.UiTextField;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.spi.TaxonPort;
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
    }

    @Getter
    private final I18nService                                              i18nService;
    private final AccessEvaluator                                          access;
    private final ComponentFactory<TaxonPort>                              taxonPortFactory;
    private final ComponentFactory<AuditPort>                              auditPortFactory;
    private final NotificationService                                      notificationService;
    @SuppressWarnings("rawtypes")
    private final UiComponentFactory<OverlayFormBinder>                    formBinderFactory;
    private final UiComponentFactory<AuditActivityPanel>                   auditActivityPanelFactory;
    private final UiComponentFactory<UiIconButton>                         cancelButtonFactory;
    private final UiPrimaryButton                                          saveButton;
    private final UiTertiaryButton                                         discardButton;
    private final UiTextField                                              nameEnField;
    private final UiTextArea                                               descriptionEnField;
    private final UiTextField                                              nameUkField;
    private final UiTextArea                                               descriptionUkField;

    private Parameters params;
    private Tabs       topTabs;
    private Tab        editTab;
    @Getter private Long savedTaxonId;

    @Override
    public TaxonFormOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        nameEnField.configure(UiTextField.Parameters.builder()
                .labelKey(TAXON_OVERLAY_FIELD_NAME)
                .placeholderKey(TAXON_OVERLAY_FIELD_NAME_PLACEHOLDER)
                .maxLength(255)
                .required(true)
                .build());
        descriptionEnField.configure(UiTextArea.Parameters.builder()
                .labelKey(TAXON_OVERLAY_FIELD_DESCRIPTION)
                .placeholderKey(TAXON_OVERLAY_FIELD_DESCRIPTION_PLACEHOLDER)
                .maxLength(2000)
                .required(true)
                .build());
        nameUkField.configure(UiTextField.Parameters.builder()
                .labelKey(TAXON_OVERLAY_FIELD_NAME)
                .placeholderKey(TAXON_OVERLAY_FIELD_NAME_PLACEHOLDER)
                .maxLength(255)
                .required(true)
                .build());
        descriptionUkField.configure(UiTextArea.Parameters.builder()
                .labelKey(TAXON_OVERLAY_FIELD_DESCRIPTION)
                .placeholderKey(TAXON_OVERLAY_FIELD_DESCRIPTION_PLACEHOLDER)
                .maxLength(2000)
                .required(true)
                .build());

        saveButton.configure(UiPrimaryButton.Parameters.builder()
                .labelKey(TAXON_OVERLAY_BUTTON_SAVE).build());
        discardButton.configure(UiTertiaryButton.Parameters.builder()
                .labelKey(FORM_DISCARD_CHANGES).build());
        UiIconButton closeBtn = cancelButtonFactory.build(UiIconButton.Parameters.builder()
                .labelKey(TAXON_OVERLAY_BUTTON_CANCEL)
                .icon(VaadinIcon.CLOSE.create())
                .build());

        wireSaveGuard(saveButton, params.getOnSave());
        discardButton.addClickListener(_ -> discardChanges());
        closeBtn.addClickListener(_ -> params.getOnCancel().run());

        TaxonEditDto dto = buildDto();
        buildBinder(dto);

        nameEnField.setValueChangeMode(ValueChangeMode.EAGER);
        descriptionEnField.setValueChangeMode(ValueChangeMode.EAGER);
        nameUkField.setValueChangeMode(ValueChangeMode.EAGER);
        descriptionUkField.setValueChangeMode(ValueChangeMode.EAGER);
        nameEnField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        descriptionEnField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        nameUkField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        descriptionUkField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));

        Div cardHeader = new Div(VaadinIcon.TAG.create(), new Span(getValue(TAXON_OVERLAY_SECTION_LABEL)));
        cardHeader.addClassName("overlay__form-card-header");

        H4 enLabel = new H4(getValue(TAXON_OVERLAY_LOCALE_TAB_EN));
        enLabel.addClassName("taxon-locale-label");
        Div enContent = new Div(nameEnField, descriptionEnField);
        enContent.addClassName("taxon-locale-content");

        H4 ukLabel = new H4(getValue(TAXON_OVERLAY_LOCALE_TAB_UK));
        ukLabel.addClassName("taxon-locale-label");
        Div ukContent = new Div(nameUkField, descriptionUkField);
        ukContent.addClassName("taxon-locale-content");

        Div fieldsCard = new Div(cardHeader, enLabel, enContent, ukLabel, ukContent);
        fieldsCard.addClassName("overlay__form-fields-card");

        Div editContent = new Div(fieldsCard);

        Div content = buildContentWithActivity(editContent);
        layout.setContent(content);
        layout.setHeaderActions(new Div(saveButton, discardButton, closeBtn));
        updateButtons(false);
    }

    public boolean save() {
        return binder.save(dto -> taxonPortFactory.ifAvailable(port -> {
            Map<Locale, TaxonTranslationDto> translations = Map.of(
                    Locale.ENGLISH,    TaxonTranslationDto.builder().locale("en").name(dto.getNameEn()).description(dto.getDescriptionEn()).build(),
                    Locale.forLanguageTag("uk"), TaxonTranslationDto.builder().locale("uk").name(dto.getNameUk()).description(dto.getDescriptionUk()).build()
            );
            if (params.getMode() == Mode.CREATE) {
                savedTaxonId = port.create(org.ost.platform.taxon.model.TaxonType.CATEGORY, translations, access.getCurrentUserId());
            } else {
                port.update(params.getTaxon().getId(), translations, access.getCurrentUserId());
                savedTaxonId = params.getTaxon().getId();
            }
        }));
    }

    public void discardChanges() {
        TaxonEditDto fresh = buildDto();
        binder.reload(fresh, (src, tgt) -> {
            tgt.setNameEn(src.getNameEn());
            tgt.setDescriptionEn(src.getDescriptionEn());
            tgt.setNameUk(src.getNameUk());
            tgt.setDescriptionUk(src.getDescriptionUk());
        });
        updateButtons(false);
    }

    public void afterSave(boolean success) {
        if (success) {
            updateButtons(false);
            if (topTabs != null) topTabs.setSelectedTab(editTab);
            if (tabbedSecondaryContent != null) tabbedSecondaryContent.removeAll();
        } else {
            updateButtons(true);
        }
    }

    private Div buildContentWithActivity(Div editContent) {
        if (params.getMode() == Mode.CREATE) {
            return editContent;
        }
        return auditActivityPanelFactory.findIfAvailable()
                .map(_ -> {
                    editTab = new Tab(getValue(TAXON_OVERLAY_TAB_EDIT));
                    Tab activityTab = new Tab(getValue(TAXON_OVERLAY_TAB_ACTIVITY));
                    topTabs = new Tabs(editTab, activityTab);
                    topTabs.addClassName("taxon-form-tabs");
                    Div result = buildTabbedContent(topTabs, editTab, editContent, this::buildActivityContent);
                    tabbedSecondaryContent.addClassName("activity-feed-content");
                    return result;
                })
                .orElse(editContent);
    }

    private com.vaadin.flow.component.Component buildActivityContent() {
        return auditActivityPanelFactory.build(AuditActivityPanel.Parameters.builder()
                .entityRef(new EntityRef(EntityType.TAXON, params.getTaxon().getId()))
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .canOperate(access.isPrivileged())
                .onRestoreRequested(this::handleRestoreFromActivity)
                .build());
    }

    private void handleRestoreFromActivity(long snapshotId) {
        auditPortFactory.ifAvailable(port ->
                port.<TaxonSnapshotDto>getSnapshotContent(snapshotId, EntityType.TAXON)
                        .ifPresent(content -> {
                            TaxonSnapshotDto snapshot = content.snapshotData();
                            TaxonEditDto dto = new TaxonEditDto();
                            dto.setId(params.getTaxon().getId());
                            dto.setNameEn(snapshot.nameEn());
                            dto.setDescriptionEn(snapshot.descriptionEn());
                            dto.setNameUk(snapshot.nameUk());
                            dto.setDescriptionUk(snapshot.descriptionUk());
                            loadRestored(dto);
                        })
        );
    }

    public void loadRestored(@NonNull TaxonEditDto restoredDto) {
        binder.loadRestored(restoredDto, (src, tgt) -> {
            tgt.setNameEn(src.getNameEn());
            tgt.setDescriptionEn(src.getDescriptionEn());
            tgt.setNameUk(src.getNameUk());
            tgt.setDescriptionUk(src.getDescriptionUk());
        });
        notificationService.success(FORM_RESTORE_BANNER);
        updateButtons(true);
        if (topTabs != null) topTabs.setSelectedTab(editTab);
    }

    private TaxonEditDto buildDto() {
        TaxonEditDto dto = new TaxonEditDto();
        if (params.getTaxon() != null) {
            dto.setId(params.getTaxon().getId());
            List<TaxonTranslationDto> translations = taxonPortFactory.findIfAvailable()
                    .map(p -> p.getTranslations(params.getTaxon().getId()))
                    .orElse(List.of());
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

    @SuppressWarnings("unchecked")
    private void buildBinder(TaxonEditDto dto) {
        binder = formBinderFactory.build(
                OverlayFormBinder.Parameters.<TaxonEditDto>builder()
                        .clazz(TaxonEditDto.class)
                        .dto(dto)
                        .build()
        );
        binder.getBinder().forField(nameEnField)
                .asRequired(getValue(TAXON_OVERLAY_VALIDATION_NAME_REQUIRED))
                .withValidator(new StringLengthValidator(getValue(TAXON_OVERLAY_VALIDATION_NAME_LENGTH), 1, 255))
                .bind(TaxonEditDto::getNameEn, TaxonEditDto::setNameEn);
        binder.getBinder().forField(descriptionEnField)
                .asRequired(getValue(TAXON_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED))
                .withValidator(new StringLengthValidator(getValue(TAXON_OVERLAY_VALIDATION_DESCRIPTION_LENGTH), 1, 2000))
                .bind(TaxonEditDto::getDescriptionEn, TaxonEditDto::setDescriptionEn);
        binder.getBinder().forField(nameUkField)
                .asRequired(getValue(TAXON_OVERLAY_VALIDATION_NAME_REQUIRED))
                .withValidator(new StringLengthValidator(getValue(TAXON_OVERLAY_VALIDATION_NAME_LENGTH), 1, 255))
                .bind(TaxonEditDto::getNameUk, TaxonEditDto::setNameUk);
        binder.getBinder().forField(descriptionUkField)
                .asRequired(getValue(TAXON_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED))
                .withValidator(new StringLengthValidator(getValue(TAXON_OVERLAY_VALIDATION_DESCRIPTION_LENGTH), 1, 2000))
                .bind(TaxonEditDto::getDescriptionUk, TaxonEditDto::setDescriptionUk);
        binder.readInitialValues();
    }

    private void updateButtons(boolean hasChanges) {
        saveButton.setEnabled(hasChanges);
        discardButton.setEnabled(hasChanges);
    }
}
