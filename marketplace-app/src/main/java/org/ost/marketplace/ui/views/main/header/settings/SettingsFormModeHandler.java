package org.ost.marketplace.ui.views.main.header.settings;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.binder.Setter;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.PaginationDefaults;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.marketplace.ui.dto.SettingsEditDto;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.components.audit.EntityActivityOverlay;
import org.ost.marketplace.ui.views.utils.BeforeUnloadUtil;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.ui.core.Configurable;
import org.springframework.context.annotation.Scope;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** {@code AccountOverlay}'s Settings tab -- has no View mode, so re-checks
 *  {@code canEditUserAccount} itself. */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class SettingsFormModeHandler extends AbstractFormOverlayModeHandler<SettingsEditDto>
        implements Configurable<SettingsFormModeHandler, SettingsFormModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull Long     userId;
        @NonNull Runnable onSave;
        @NonNull Runnable onCancel;
        @NonNull List<BreadcrumbStep> breadcrumbSteps;
        @NonNull Component tabBar;
    }

    @Getter
    private final I18nService                                       i18nService;
    private final UserProfileService                                userProfileService;
    private final AccessEvaluator                                   access;
    private final UiComponentFactory<OverlayFormBinder<SettingsEditDto>, OverlayFormBinder.Parameters<SettingsEditDto>> formBinderFactory;
    private final AuditQueryService                                       auditQueryService;
    private final EntityActivityOverlay                                  entityActivityOverlay;

    private Parameters       params;
    private IntegerField     adsPageSizeField;
    private IntegerField     usersPageSizeField;
    private IntegerField     timelinePageSizeField;
    private IntegerField     providerProfilesPageSizeField;
    private IntegerField     feedbackPageSizeField;
    private IntegerField     moderationPageSizeField;
    private UiPrimaryButton  saveButton;
    private UiTertiaryButton discardButton;

    @Override
    public SettingsFormModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        UserSettingsDto current = userProfileService.loadSettings(params.getUserId());
        SettingsEditDto dto = toEditDto(params.getUserId(), current);

        // Settings has no View mode to gate an Edit button on, unlike Name/Provider Profile.
        boolean canEdit = access.canEditUserAccount(params.getUserId());

        saveButton = new UiPrimaryButton(getValue(SETTINGS_SAVE_BUTTON));
        saveButton.setVisible(canEdit);
        discardButton = new UiTertiaryButton(getValue(FORM_DISCARD_CHANGES));
        discardButton.setVisible(canEdit);
        UiIconButton closeBtn = new UiIconButton(getValue(HEADER_HOME), VaadinIcon.CLOSE.create());

        wireSaveGuard(saveButton, params.getOnSave());
        discardButton.addClickListener(_ -> discardChanges());
        closeBtn.addClickListener(_ -> params.getOnCancel().run());

        buildBinder(dto);
        adsPageSizeField.setReadOnly(!canEdit);
        providerProfilesPageSizeField.setReadOnly(!canEdit);
        feedbackPageSizeField.setReadOnly(!canEdit);
        boolean privileged = access.canView();
        adsPageSizeField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        providerProfilesPageSizeField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        feedbackPageSizeField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        if (privileged) {
            usersPageSizeField.setReadOnly(!canEdit);
            timelinePageSizeField.setReadOnly(!canEdit);
            moderationPageSizeField.setReadOnly(!canEdit);
            usersPageSizeField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
            timelinePageSizeField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
            moderationPageSizeField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        }

        Div cardHeader = new Div(VaadinIcon.COG.create(), new Span(getValue(SETTINGS_SECTION_TITLE)));
        cardHeader.addClassName("overlay__form-card-header");

        Div fieldsCard = privileged
                ? new Div(cardHeader, adsPageSizeField, providerProfilesPageSizeField, feedbackPageSizeField, usersPageSizeField, timelinePageSizeField, moderationPageSizeField)
                : new Div(cardHeader, adsPageSizeField, providerProfilesPageSizeField, feedbackPageSizeField);
        fieldsCard.addClassName("overlay__form-fields-card");

        Div settingsContent = new Div(params.getTabBar(), fieldsCard);
        settingsContent.addClassName("settings-overlay-content");
        layout.setContent(settingsContent);

        Div headerActions = new Div(saveButton, discardButton);
        if (auditQueryService.isAvailable()) {
            headerActions.add(buildHistoryButton());
        }
        headerActions.add(closeBtn);
        layout.setHeaderActions(headerActions);
        updateButtons(false);
    }

    private UiIconButton buildHistoryButton() {
        UiIconButton historyBtn = new UiIconButton(getValue(SETTINGS_ACTIVITY_BUTTON), VaadinIcon.CLOCK.create());
        historyBtn.addClassName("settings-history-button");
        historyBtn.addClickListener(_ -> entityActivityOverlay.openFor(EntityActivityOverlay.Parameters.builder()
                .entityRef(new EntityRef(EntityType.USER_SETTINGS, params.getUserId()))
                .userId(params.getUserId())
                .isPrivileged(true)
                .canOperate(true)
                .parentSteps(params.getBreadcrumbSteps())
                .parentFormLabel(getValue(SETTINGS_SECTION_TITLE))
                .currentLabelKey(SETTINGS_ACTIVITY_BUTTON)
                .onRestoreRequested(this::handleRestoreFromActivity)
                .build()));
        return historyBtn;
    }

    public boolean save() {
        return binder.save(dto -> userProfileService.saveSettings(dto.getId(), UserSettingsDto.builder()
                .adsPageSize(dto.getAdsPageSize() != null ? dto.getAdsPageSize() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .usersPageSize(dto.getUsersPageSize() != null ? dto.getUsersPageSize() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .timelinePageSize(dto.getTimelinePageSize() != null ? dto.getTimelinePageSize() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .providerProfilesPageSize(dto.getProviderProfilesPageSize() != null ? dto.getProviderProfilesPageSize() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .feedbackPageSize(dto.getFeedbackPageSize() != null ? dto.getFeedbackPageSize() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .moderationPageSize(dto.getModerationPageSize() != null ? dto.getModerationPageSize() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .version(dto.getVersion())
                .build()));
    }

    public void afterSave(boolean success) {
        updateButtons(!success);
    }

    public void discardChanges() {
        UserSettingsDto fresh = userProfileService.loadSettings(params.getUserId());
        binder.reload(toEditDto(params.getUserId(), fresh), SettingsFormModeHandler::copySettings);
        updateButtons(false);
    }

    private void handleRestoreFromActivity(Long snapshotId) {
        auditQueryService.getSnapshotContent(snapshotId, EntityType.USER_SETTINGS, SettingsSnapshotDto.class)
                .map(c -> UserSettingsDto.builder()
                        .adsPageSize(c.snapshotData().adsPageSize())
                        .usersPageSize(c.snapshotData().usersPageSize())
                        .timelinePageSize(c.snapshotData().timelinePageSize())
                        .providerProfilesPageSize(c.snapshotData().providerProfilesPageSize())
                        .feedbackPageSize(c.snapshotData().feedbackPageSize())
                        .moderationPageSize(c.snapshotData().moderationPageSize())
                        // Restore only stages values into the form -- the eventual save()
                        // still checks against the current DB version, never the snapshot's
                        // (snapshots don't carry one; they predate optimistic locking here).
                        .version(userProfileService.loadSettings(params.getUserId()).getVersion())
                        .build())
                .ifPresent(this::loadRestored);
    }

    private void loadRestored(UserSettingsDto restored) {
        binder.loadRestored(toEditDto(params.getUserId(), restored), SettingsFormModeHandler::copySettings);
        updateButtons(true);
    }

    private static SettingsEditDto toEditDto(Long userId, UserSettingsDto source) {
        return SettingsEditDto.builder().id(userId)
                .adsPageSize(source.getAdsPageSize()).usersPageSize(source.getUsersPageSize())
                .timelinePageSize(source.getTimelinePageSize()).providerProfilesPageSize(source.getProviderProfilesPageSize())
                .feedbackPageSize(source.getFeedbackPageSize()).moderationPageSize(source.getModerationPageSize())
                .version(source.getVersion()).build();
    }

    private static void copySettings(SettingsEditDto src, SettingsEditDto tgt) {
        tgt.setAdsPageSize(src.getAdsPageSize()); tgt.setUsersPageSize(src.getUsersPageSize());
        tgt.setTimelinePageSize(src.getTimelinePageSize()); tgt.setProviderProfilesPageSize(src.getProviderProfilesPageSize());
        tgt.setFeedbackPageSize(src.getFeedbackPageSize()); tgt.setModerationPageSize(src.getModerationPageSize());
        tgt.setVersion(src.getVersion());
    }

    private void buildBinder(SettingsEditDto dto) {
        binder = formBinderFactory.build(
                OverlayFormBinder.Parameters.<SettingsEditDto>builder()
                        .clazz(SettingsEditDto.class)
                        .dto(dto)
                        .build());

        adsPageSizeField               = newPageSizeField(getValue(SETTINGS_ADS_PAGE_SIZE_LABEL));
        usersPageSizeField             = newPageSizeField(getValue(SETTINGS_USERS_PAGE_SIZE_LABEL));
        timelinePageSizeField          = newPageSizeField(getValue(SETTINGS_TIMELINE_PAGE_SIZE_LABEL));
        providerProfilesPageSizeField  = newPageSizeField(getValue(SETTINGS_PROVIDER_PROFILES_PAGE_SIZE_LABEL));
        feedbackPageSizeField          = newPageSizeField(getValue(SETTINGS_FEEDBACK_PAGE_SIZE_LABEL));
        moderationPageSizeField        = newPageSizeField(getValue(SETTINGS_MODERATION_PAGE_SIZE_LABEL));

        bindPageSizeField(adsPageSizeField,               SettingsEditDto::getAdsPageSize,               SettingsEditDto::setAdsPageSize);
        bindPageSizeField(usersPageSizeField,             SettingsEditDto::getUsersPageSize,             SettingsEditDto::setUsersPageSize);
        bindPageSizeField(timelinePageSizeField,          SettingsEditDto::getTimelinePageSize,          SettingsEditDto::setTimelinePageSize);
        bindPageSizeField(providerProfilesPageSizeField,  SettingsEditDto::getProviderProfilesPageSize,  SettingsEditDto::setProviderProfilesPageSize);
        bindPageSizeField(feedbackPageSizeField,          SettingsEditDto::getFeedbackPageSize,          SettingsEditDto::setFeedbackPageSize);
        bindPageSizeField(moderationPageSizeField,        SettingsEditDto::getModerationPageSize,        SettingsEditDto::setModerationPageSize);
        binder.readInitialValues();
    }

    private void bindPageSizeField(IntegerField field,
            ValueProvider<SettingsEditDto, Integer> getter,
            Setter<SettingsEditDto, Integer> setter) {
        binder.getBinder().forField(field)
                .asRequired()
                .withValidator(
                        v -> v >= PaginationDefaults.MIN_PAGE_SIZE && v <= PaginationDefaults.MAX_PAGE_SIZE,
                        getValue(SETTINGS_PAGE_SIZE_RANGE))
                .bind(getter, setter);
    }

    private static IntegerField newPageSizeField(String label) {
        IntegerField field = new IntegerField(label);
        field.setMin(PaginationDefaults.MIN_PAGE_SIZE); field.setMax(PaginationDefaults.MAX_PAGE_SIZE);
        field.setStep(1); field.setStepButtonsVisible(true);
        field.setValueChangeMode(ValueChangeMode.EAGER); field.setWidthFull();
        return field;
    }

    private void updateButtons(boolean hasChanges) {
        saveButton.setEnabled(hasChanges);
        discardButton.setEnabled(hasChanges);
        BeforeUnloadUtil.sync(hasChanges);
    }
}
