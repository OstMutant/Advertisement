package org.ost.marketplace.ui.views.main.header.settings;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.common.PaginationDefaults;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.user.services.UserSettingsService;
import org.ost.marketplace.ui.dto.SettingsEditDto;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.spi.audit.AuditUiPort;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.common.I18nKey.*;

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
    }

    @Getter
    private final I18nService                                       i18nService;
    private final UserSettingsService                               settingsService;
    private final transient ComponentFactory<OverlayFormBinder>    formBinderFactory;
    private final transient ComponentFactory<AuditPort>            auditPortFactory;
    private final transient ComponentFactory<AuditUiPort>          auditUiPortFactory;
    private final transient ComponentFactory<UiIconButton>         cancelButtonFactory;
    private final UiPrimaryButton                                   saveButton;
    private final UiTertiaryButton                                  discardButton;

    private Parameters   params;
    private IntegerField adsPageSizeField;
    private IntegerField usersPageSizeField;
    private Tabs         formTabs;
    private Tab          settingsTab;
    private Div          historyContent;
    private Div          timelineContent;

    @Override
    public SettingsFormModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        UserSettingsDto current = settingsService.load(params.getUserId());
        SettingsEditDto dto = SettingsEditDto.builder()
                .id(params.getUserId())
                .adsPageSize(current.getAdsPageSize())
                .usersPageSize(current.getUsersPageSize())
                .build();

        saveButton.configure(UiPrimaryButton.Parameters.builder().labelKey(SETTINGS_SAVE_BUTTON).build());
        discardButton.configure(UiTertiaryButton.Parameters.builder().labelKey(FORM_DISCARD_CHANGES).build());
        UiIconButton closeBtn = cancelButtonFactory.build(UiIconButton.Parameters.builder()
                .labelKey(HEADER_HOME)
                .icon(VaadinIcon.CLOSE.create())
                .build());

        wireSaveGuard(saveButton, params.getOnSave());
        discardButton.addClickListener(_ -> discardChanges());
        closeBtn.addClickListener(_ -> params.getOnCancel().run());

        buildBinder(dto);
        adsPageSizeField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));
        usersPageSizeField.addValueChangeListener(_ -> updateButtons(binder.hasChanges()));

        Div cardHeader = new Div(VaadinIcon.COG.create(), new Span(getValue(SETTINGS_SECTION_TITLE)));
        cardHeader.addClassName("overlay__form-card-header");

        Div fieldsCard = new Div(cardHeader, adsPageSizeField, usersPageSizeField);
        fieldsCard.addClassName("overlay__form-fields-card");

        Div settingsContent = new Div(fieldsCard);

        Div content = auditUiPortFactory.findIfAvailable()
                .map(auditUi -> {
                    settingsTab       = new Tab(getValue(SETTINGS_SECTION_TITLE));
                    Tab historyTab    = new Tab(getValue(SETTINGS_ACTIVITY_TAB));
                    Tab timelineTab   = new Tab(getValue(TIMELINE_TAB));
                    formTabs          = new Tabs(settingsTab, historyTab, timelineTab);
                    formTabs.addClassName("user-view-tabs");

                    historyContent  = new Div();
                    historyContent.setVisible(false);
                    timelineContent = new Div();
                    timelineContent.setVisible(false);

                    formTabs.addSelectedChangeListener(event -> {
                        Tab selected     = event.getSelectedTab();
                        boolean isSettings = selected == settingsTab;
                        boolean isHistory  = selected == historyTab;
                        settingsContent.setVisible(isSettings);
                        historyContent.setVisible(isHistory);
                        timelineContent.setVisible(!isSettings && !isHistory);
                        if (isHistory && historyContent.getChildren().findFirst().isEmpty()) {
                            historyContent.add(buildHistoryContent(auditUi));
                        }
                        if (!isSettings && !isHistory && timelineContent.getChildren().findFirst().isEmpty()) {
                            timelineContent.add(buildTimelineContent(auditUi));
                        }
                    });

                    return new Div(formTabs, settingsContent, historyContent, timelineContent);
                })
                .orElse(settingsContent);

        content.addClassName("settings-overlay-content");
        layout.setContent(content);
        layout.setHeaderActions(new Div(saveButton, discardButton, closeBtn));
        updateButtons(false);
    }

    public boolean save() {
        return binder.save(dto -> settingsService.save(dto.getId(), UserSettingsDto.builder()
                .adsPageSize(dto.getAdsPageSize() != null ? dto.getAdsPageSize() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .usersPageSize(dto.getUsersPageSize() != null ? dto.getUsersPageSize() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .build()));
    }

    public void afterSave(boolean success) {
        if (success) {
            if (historyContent  != null) historyContent.removeAll();
            if (timelineContent != null) timelineContent.removeAll();
            if (formTabs        != null) formTabs.setSelectedTab(settingsTab);
            updateButtons(false);
        } else {
            updateButtons(true);
        }
    }

    public void discardChanges() {
        UserSettingsDto fresh = settingsService.load(params.getUserId());
        binder.reload(
                SettingsEditDto.builder()
                        .id(params.getUserId())
                        .adsPageSize(fresh.getAdsPageSize())
                        .usersPageSize(fresh.getUsersPageSize())
                        .build(),
                (src, tgt) -> {
                    tgt.setAdsPageSize(src.getAdsPageSize());
                    tgt.setUsersPageSize(src.getUsersPageSize());
                });
        updateButtons(false);
    }

    private com.vaadin.flow.component.Component buildHistoryContent(AuditUiPort auditUi) {
        return auditUi.buildAuditActivityPanel(AuditUiPort.ActivityParams.builder()
                .entityRef(new EntityRef(EntityType.USER_SETTINGS, params.getUserId()))
                .userId(params.getUserId())
                .isPrivileged(true)
                .canOperate(true)
                .onRestoreRequested(snapshotId -> handleRestoreFromActivity(snapshotId))
                .build());
    }

    private com.vaadin.flow.component.Component buildTimelineContent(AuditUiPort auditUi) {
        return auditUi.buildAuditTimelinePanel(AuditUiPort.TimelineParams.builder()
                .actorId(params.getUserId())
                .viewerActorId(params.getUserId())
                .build());
    }

    private void handleRestoreFromActivity(Long snapshotId) {
        auditPortFactory.ifAvailable(port ->
                port.<SettingsSnapshotDto>getSnapshotContent(snapshotId, EntityType.USER_SETTINGS)
                        .map(c -> UserSettingsDto.builder()
                                .adsPageSize(c.snapshotData().adsPageSize())
                                .usersPageSize(c.snapshotData().usersPageSize())
                                .build())
                        .ifPresent(this::loadRestored));
    }

    private void loadRestored(UserSettingsDto restored) {
        binder.loadRestored(
                SettingsEditDto.builder()
                        .id(params.getUserId())
                        .adsPageSize(restored.getAdsPageSize())
                        .usersPageSize(restored.getUsersPageSize())
                        .build(),
                (src, tgt) -> {
                    tgt.setAdsPageSize(src.getAdsPageSize());
                    tgt.setUsersPageSize(src.getUsersPageSize());
                });
        updateButtons(true);
        if (formTabs != null) formTabs.setSelectedTab(settingsTab);
    }

    @SuppressWarnings("unchecked")
    private void buildBinder(SettingsEditDto dto) {
        binder = formBinderFactory.build(
                OverlayFormBinder.Parameters.<SettingsEditDto>builder()
                        .clazz(SettingsEditDto.class)
                        .dto(dto)
                        .build());

        adsPageSizeField = new IntegerField(getValue(SETTINGS_ADS_PAGE_SIZE_LABEL));
        adsPageSizeField.setMin(1);
        adsPageSizeField.setMax(PaginationDefaults.MAX_PAGE_SIZE);
        adsPageSizeField.setStep(1);
        adsPageSizeField.setStepButtonsVisible(true);
        adsPageSizeField.setValueChangeMode(ValueChangeMode.EAGER);
        adsPageSizeField.setWidthFull();

        usersPageSizeField = new IntegerField(getValue(SETTINGS_USERS_PAGE_SIZE_LABEL));
        usersPageSizeField.setMin(1);
        usersPageSizeField.setMax(PaginationDefaults.MAX_PAGE_SIZE);
        usersPageSizeField.setStep(1);
        usersPageSizeField.setStepButtonsVisible(true);
        usersPageSizeField.setValueChangeMode(ValueChangeMode.EAGER);
        usersPageSizeField.setWidthFull();

        binder.getBinder().forField(adsPageSizeField)
                .asRequired()
                .bind(SettingsEditDto::getAdsPageSize, SettingsEditDto::setAdsPageSize);
        binder.getBinder().forField(usersPageSizeField)
                .asRequired()
                .bind(SettingsEditDto::getUsersPageSize, SettingsEditDto::setUsersPageSize);
        binder.readInitialValues();
    }

    private void updateButtons(boolean hasChanges) {
        saveButton.setEnabled(hasChanges);
        discardButton.setEnabled(hasChanges);
    }
}
