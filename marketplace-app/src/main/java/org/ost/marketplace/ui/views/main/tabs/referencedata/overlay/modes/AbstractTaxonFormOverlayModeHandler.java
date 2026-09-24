package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import lombok.NonNull;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.dto.EditDto;
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
import org.ost.platform.taxon.model.TaxonType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.ost.marketplace.services.i18n.I18nKey.FORM_DISCARD_CHANGES;
import static org.ost.marketplace.services.i18n.I18nKey.FORM_RESTORE_BANNER;

/** Shared save/discard/restore/history lifecycle for a taxon-backed edit form, parameterized by the concrete City/Category subclass's DTO type, services, and labels. */
public abstract class AbstractTaxonFormOverlayModeHandler<T extends EditDto> extends AbstractFormOverlayModeHandler<T> implements I18nParams {

    public enum Mode { CREATE, EDIT }

    public record Parameters(
            TaxonDto              entity,
            @NonNull Mode         mode,
            @NonNull Runnable     onSave,
            @NonNull Runnable     onCancel,
            @NonNull List<BreadcrumbStep> breadcrumbSteps
    ) {
        Parameters withEntity(TaxonDto fresh) { return new Parameters(fresh, mode, onSave, onCancel, breadcrumbSteps); }
    }

    /** The domain-specific i18n keys this handler needs -- everything else is already covered by {@link LocaleTranslationForm.Labels}. */
    public record Labels(I18nKey sectionLabel, I18nKey buttonSave, I18nKey buttonCancel,
                          I18nKey titleEdit, I18nKey activityButton) {}

    protected abstract TaxonCatalogService getTaxonCatalogService();
    protected abstract AccessEvaluator     getAccess();
    protected abstract NotificationService getNotificationService();
    protected abstract AuditQueryService   getAuditQueryService();
    protected abstract EntityActivityOverlay getEntityActivityOverlay();
    protected abstract UiComponentFactory<OverlayFormBinder<T>, OverlayFormBinder.Parameters<T>> getFormBinderFactory();
    protected abstract TaxonType  getTaxonType();
    protected abstract Class<T>   getDtoClass();
    protected abstract T          newDto();
    protected abstract LocaleTranslationForm<T> buildLocaleForm();
    protected abstract String     getCssPrefix();
    protected abstract Labels     getLabels();

    protected Parameters params;
    protected Long savedEntityId;
    private LocaleTranslationForm<T> localeForm;
    private UiPrimaryButton   saveButton;
    private UiTertiaryButton  discardButton;

    public Long getSavedEntityId() { return savedEntityId; }

    @Override
    public void activate(OverlayLayout layout) {
        localeForm = buildLocaleForm();

        saveButton = new UiPrimaryButton(getValue(getLabels().buttonSave()));
        discardButton = new UiTertiaryButton(getValue(FORM_DISCARD_CHANGES));
        UiIconButton closeBtn = new UiIconButton(getValue(getLabels().buttonCancel()), VaadinIcon.CLOSE.create());

        wireSaveGuard(saveButton, params.onSave());
        discardButton.addClickListener(_ -> discardChanges());
        closeBtn.addClickListener(_ -> params.onCancel().run());

        T dto = buildDto();
        buildBinder(dto);
        localeForm.wireValueChangeListeners(() -> updateButtons(binder.hasChanges()));

        Div fieldsCard = localeForm.buildFieldsCard(VaadinIcon.TAG.create(), getValue(getLabels().sectionLabel()));
        Div editContent = new Div(fieldsCard);
        layout.setContent(editContent);

        Div headerActions = new Div(saveButton, discardButton);
        if (params.mode() != Mode.CREATE && getAuditQueryService().isAvailable()) {
            headerActions.add(buildHistoryButton());
        }
        headerActions.add(closeBtn);
        layout.setHeaderActions(headerActions);
        updateButtons(false);
    }

    private UiIconButton buildHistoryButton() {
        UiIconButton historyBtn = new UiIconButton(getValue(getLabels().activityButton()), VaadinIcon.CLOCK.create());
        historyBtn.addClassName(getCssPrefix() + "-history-button");
        historyBtn.addClickListener(_ -> getEntityActivityOverlay().openFor(EntityActivityOverlay.Parameters.builder()
                .entityRef(new EntityRef(EntityType.TAXON, params.entity().getId()))
                .userId(getAccess().getCurrentUserId())
                .isPrivileged(getAccess().isPrivileged())
                .canOperate(getAccess().isPrivileged())
                .parentSteps(params.breadcrumbSteps())
                .parentFormLabel(getValue(getLabels().titleEdit()))
                .currentLabelKey(getLabels().activityButton())
                .onRestoreRequested(this::handleRestoreFromActivity)
                .build()));
        return historyBtn;
    }

    public boolean save() {
        return binder.save(dto -> {
            if (!getTaxonCatalogService().isAvailable()) return;
            Map<Locale, TaxonTranslationDto> translations = localeForm.extractTranslations(dto);
            if (params.mode() == Mode.CREATE) {
                savedEntityId = getTaxonCatalogService().create(getTaxonType(), translations, getAccess().getCurrentUserId());
            } else {
                getTaxonCatalogService().update(params.entity().getId(), translations, getAccess().getCurrentUserId(), params.entity().getVersion());
                savedEntityId = params.entity().getId();
            }
            getTaxonCatalogService().findById(savedEntityId, Locale.ENGLISH)
                    .ifPresent(fresh -> params = params.withEntity(fresh));
        });
    }

    public void discardChanges() {
        T fresh = buildDto();
        binder.reload(fresh, localeForm::copyLocaleFields);
        updateButtons(false);
    }

    public void afterSave(boolean success) {
        updateButtons(!success);
    }

    private void handleRestoreFromActivity(long snapshotId) {
        getAuditQueryService().getSnapshotContent(snapshotId, EntityType.TAXON, TaxonSnapshotDto.class)
                .ifPresent(content -> {
                    TaxonSnapshotDto snapshot = content.snapshotData();
                    T dto = newDto();
                    dto.setId(params.entity().getId());
                    localeForm.restoreFromSnapshot(snapshot, dto);
                    loadRestored(dto);
                });
    }

    public void loadRestored(@NonNull T restoredDto) {
        binder.loadRestored(restoredDto, localeForm::copyLocaleFields);
        getNotificationService().success(FORM_RESTORE_BANNER);
        updateButtons(true);
    }

    private T buildDto() {
        T dto = newDto();
        Long id = params.entity() != null ? params.entity().getId() : savedEntityId;
        if (id != null) {
            dto.setId(id);
            localeForm.applyTranslations(dto, getTaxonCatalogService().getTranslations(id));
        }
        return dto;
    }

    private void buildBinder(T dto) {
        binder = getFormBinderFactory().build(
                OverlayFormBinder.Parameters.<T>builder()
                        .clazz(getDtoClass())
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
