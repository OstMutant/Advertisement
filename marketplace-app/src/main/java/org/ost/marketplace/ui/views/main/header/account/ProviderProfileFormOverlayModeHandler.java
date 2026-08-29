package org.ost.marketplace.ui.views.main.header.account;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jsoup.Jsoup;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.ui.dto.ProviderProfileEditDto;
import org.ost.marketplace.ui.mappers.ProviderProfileMapper;
import org.ost.marketplace.ui.views.components.audit.EntityActivityOverlay;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.fields.QuillEditor;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.utils.BeforeUnloadUtil;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** {@code AccountOverlay}'s Provider Profile section, Edit mode -- re-checks
 *  {@code canEditUserAccount} itself, same defensive shape as Name/Settings. */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ProviderProfileFormOverlayModeHandler extends AbstractFormOverlayModeHandler<ProviderProfileEditDto>
        implements Configurable<ProviderProfileFormOverlayModeHandler, ProviderProfileFormOverlayModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull Long targetUserId;
        @NonNull Runnable onSave;
        @NonNull Runnable onCancel;
        @NonNull List<BreadcrumbStep> breadcrumbSteps;
        @NonNull Component tabBar;
    }

    private final ProviderProfileSaveService                                providerProfileSaveService;
    private final ProviderProfileMapper                                     mapper;
    private final AccessEvaluator                                           access;
    @Getter
    private final I18nService                                               i18nService;
    private final LocaleProvider                                            localeProvider;
    private final NotificationService                                       notificationService;
    private final UiComponentFactory<OverlayFormBinder<ProviderProfileEditDto>> formBinderFactory;
    private final AuditQueryService                                         auditQueryService;
    private final EntityActivityOverlay                                     entityActivityOverlay;
    private final TaxonCatalogService                                       taxonCatalogService;

    private Parameters params;
    private ProviderProfileDto currentProfile;
    private RadioButtonGroup<ProviderKind> kindField;
    private QuillEditor                    aboutField;
    private MultiSelectComboBox<TaxonDto>  categoryComboBox;
    private ComboBox<TaxonDto>             cityComboBox;
    private UiPrimaryButton  saveButton;
    private UiTertiaryButton discardButton;

    @Override
    public ProviderProfileFormOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        currentProfile = providerProfileSaveService.findByActorId(params.getTargetUserId()).orElse(null);
        boolean canEdit = access.canEditUserAccount(params.getTargetUserId());
        boolean canSetSupport = access.isPrivileged();

        kindField = new RadioButtonGroup<>();
        kindField.setLabel(getValue(PROVIDER_PROFILE_OVERLAY_FIELD_KIND));
        kindField.setItems(canSetSupport ? Arrays.asList(ProviderKind.values())
                : Arrays.stream(ProviderKind.values()).filter(k -> k != ProviderKind.SUPPORT).toList());

        aboutField = new QuillEditor();
        aboutField.setLabel(getValue(PROVIDER_PROFILE_OVERLAY_FIELD_ABOUT));
        aboutField.setMaxLength(ProviderProfileSaveDto.ABOUT_MAX_LENGTH);
        aboutField.addClassName("overlay__description-rich-editor");

        List<TaxonDto> availableCategories = taxonCatalogService.getAllByType(TaxonType.CATEGORY, localeProvider.getCurrentLocale());
        categoryComboBox = new MultiSelectComboBox<>();
        categoryComboBox.setLabel(getValue(PROVIDER_PROFILE_OVERLAY_FIELD_CATEGORIES));
        categoryComboBox.setItemLabelGenerator(TaxonDto::getName);
        categoryComboBox.setItems(availableCategories);

        List<TaxonDto> availableCities = taxonCatalogService.getAllByType(TaxonType.CITY, localeProvider.getCurrentLocale());
        cityComboBox = new ComboBox<>();
        cityComboBox.setLabel(getValue(PROVIDER_PROFILE_OVERLAY_FIELD_CITY));
        cityComboBox.setItemLabelGenerator(TaxonDto::getName);
        cityComboBox.setItems(availableCities);
        cityComboBox.setClearButtonVisible(true);

        kindField.setReadOnly(!canEdit);
        aboutField.setReadOnly(!canEdit);
        categoryComboBox.setReadOnly(!canEdit);
        cityComboBox.setReadOnly(!canEdit);

        ProviderProfileEditDto dto = currentProfile != null
                ? mapper.toProviderProfileEdit(currentProfile)
                : ProviderProfileEditDto.builder().kind(ProviderKind.MASTER).build();
        buildBinder(dto, availableCategories, availableCities);

        kindField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        aboutField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        categoryComboBox.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        cityComboBox.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));

        Div cardHeader = new Div(VaadinIcon.BRIEFCASE.create(), new Span(getValue(PROVIDER_PROFILE_OVERLAY_SECTION_LABEL)));
        cardHeader.addClassName("overlay__form-card-header");

        Div fieldsCard = new Div(cardHeader, kindField, aboutField, categoryComboBox, cityComboBox);
        fieldsCard.addClassName("overlay__form-fields-card");

        layout.setContent(new Div(params.getTabBar(), fieldsCard));

        saveButton = new UiPrimaryButton(getValue(PROVIDER_PROFILE_OVERLAY_BUTTON_SAVE));
        discardButton = new UiTertiaryButton(getValue(FORM_DISCARD_CHANGES));
        UiIconButton closeBtn = new UiIconButton(getValue(PROVIDER_PROFILE_OVERLAY_BUTTON_CANCEL), VaadinIcon.CLOSE.create());
        saveButton.setVisible(canEdit);
        discardButton.setVisible(canEdit);

        wireSaveGuard(saveButton, params.getOnSave());
        discardButton.addClickListener(_ -> discardChanges());
        closeBtn.addClickListener(_ -> params.getOnCancel().run());

        Div headerActions = new Div(saveButton, discardButton);
        if (currentProfile != null && auditQueryService.isAvailable()) {
            headerActions.add(buildHistoryButton());
        }
        headerActions.add(closeBtn);
        layout.setHeaderActions(headerActions);
        updateButtons(false);
    }

    private UiIconButton buildHistoryButton() {
        UiIconButton historyBtn = new UiIconButton(getValue(PROVIDER_PROFILE_ACTIVITY_BUTTON), VaadinIcon.CLOCK.create());
        historyBtn.addClassName("provider-profile-history-button");
        historyBtn.addClickListener(_ -> entityActivityOverlay.openFor(EntityActivityOverlay.Parameters.builder()
                .entityRef(new EntityRef(EntityType.PROVIDER_PROFILE, currentProfile.getId()))
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .canOperate(true)
                .parentSteps(params.getBreadcrumbSteps())
                .parentFormLabel(getValue(PROVIDER_PROFILE_OVERLAY_SECTION_LABEL))
                .currentLabelKey(PROVIDER_PROFILE_ACTIVITY_BUTTON)
                .onRestoreRequested(this::handleRestoreFromActivity)
                .build()));
        return historyBtn;
    }

    @Override
    public boolean save() {
        return binder.save(dto -> {
            if (!providerProfileSaveService.isAvailable()) return;
            Long id = providerProfileSaveService.save(
                    new ProviderProfileSaveDto(dto.getId(), dto.getKind(), dto.getAbout(),
                            dto.getCategoryIds(), dto.getCityTaxonId(), dto.getVersion()),
                    params.getTargetUserId(), access.getCurrentUserId(), access.isPrivileged());
            providerProfileSaveService.findById(id).ifPresent(saved -> {
                currentProfile = saved;
                dto.setId(saved.getId());
                dto.setVersion(saved.getVersion());
            });
        });
    }

    public void loadRestored(@NonNull ProviderProfileEditDto restoredDto) {
        binder.loadRestored(restoredDto, this::copyEditFields);
        notificationService.success(FORM_RESTORE_BANNER);
        updateButtons(true);
    }

    private void handleRestoreFromActivity(Long snapshotId) {
        auditQueryService.getSnapshotContent(snapshotId, EntityType.PROVIDER_PROFILE, ProviderProfileSnapshotDto.class)
                .map(AuditSnapshotContentDto::snapshotData)
                .ifPresent(snapshot -> {
                    ProviderProfileEditDto dto = ProviderProfileEditDto.builder()
                            .id(currentProfile.getId())
                            .kind(snapshot.kind())
                            .about(snapshot.about())
                            .categoryIds(snapshot.categoryIds() != null ? new HashSet<>(snapshot.categoryIds()) : new HashSet<>())
                            .cityTaxonId(snapshot.cityTaxonId())
                            .version(currentProfile.getVersion())
                            .build();
                    loadRestored(dto);
                });
    }

    @Override
    public void discardChanges() {
        if (currentProfile == null) {
            binder.reload(ProviderProfileEditDto.builder().kind(ProviderKind.MASTER).build(), this::copyEditFields);
            updateButtons(false);
            return;
        }
        providerProfileSaveService.findById(currentProfile.getId()).ifPresent(fresh -> {
            ProviderProfileEditDto dto = mapper.toProviderProfileEdit(fresh);
            binder.reload(dto, this::copyEditFields);
            updateButtons(false);
        });
    }

    private void copyEditFields(ProviderProfileEditDto src, ProviderProfileEditDto tgt) {
        tgt.setKind(src.getKind());
        tgt.setAbout(src.getAbout());
        tgt.setCategoryIds(src.getCategoryIds());
        tgt.setCityTaxonId(src.getCityTaxonId());
    }

    @Override
    public void afterSave(boolean success) {
        updateButtons(!success);
    }

    private void updateButtons(boolean hasChanges) {
        saveButton.setEnabled(hasChanges);
        discardButton.setEnabled(hasChanges);
        BeforeUnloadUtil.sync(hasChanges);
    }

    private void buildBinder(ProviderProfileEditDto dto, List<TaxonDto> availableCategories, List<TaxonDto> availableCities) {
        binder = formBinderFactory.build(
                OverlayFormBinder.Parameters.<ProviderProfileEditDto>builder()
                        .clazz(ProviderProfileEditDto.class)
                        .dto(dto)
                        .build());
        binder.getBinder().forField(kindField)
                .asRequired(getValue(PROVIDER_PROFILE_OVERLAY_VALIDATION_KIND_REQUIRED))
                .bind(ProviderProfileEditDto::getKind, ProviderProfileEditDto::setKind);
        binder.getBinder().forField(aboutField)
                .withValidator(
                        html -> html == null || Jsoup.parse(html).text().length() <= ProviderProfileSaveDto.ABOUT_MAX_LENGTH,
                        getValue(PROVIDER_PROFILE_OVERLAY_VALIDATION_ABOUT_LENGTH))
                .bind(ProviderProfileEditDto::getAbout, ProviderProfileEditDto::setAbout);
        binder.getBinder().forField(categoryComboBox)
                .withConverter(
                        selected -> selected.stream().map(TaxonDto::getId).collect(Collectors.toSet()),
                        ids -> ids == null ? Set.of() : availableCategories.stream()
                                .filter(t -> ids.contains(t.getId()))
                                .collect(Collectors.toSet()))
                .bind(ProviderProfileEditDto::getCategoryIds, ProviderProfileEditDto::setCategoryIds);
        binder.getBinder().forField(cityComboBox)
                .withConverter(
                        selected -> selected == null ? null : selected.getId(),
                        id -> id == null ? null : availableCities.stream()
                                .filter(t -> t.getId().equals(id))
                                .findFirst().orElse(null))
                .bind(ProviderProfileEditDto::getCityTaxonId, ProviderProfileEditDto::setCityTaxonId);
        binder.readInitialValues();
    }
}
