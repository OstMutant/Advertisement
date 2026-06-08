package org.ost.marketplace.ui.views.main.header.settings;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.entities.User;
import org.ost.platform.core.i18n.I18nService;
import org.ost.marketplace.dto.audit.SettingsSnapshotDto;
import org.ost.marketplace.services.user.UserSettingsService;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.overlay.BaseOverlay;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.platform.audit.spi.AuditUiPort;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.ComponentFactory;

import org.ost.marketplace.common.PaginationDefaults;

import static org.ost.marketplace.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class SettingsOverlay extends BaseOverlay implements I18nParams {

    @Getter
    private final transient I18nService i18nService;
    private final transient UserSettingsService                    settingsService;
    private final transient NotificationService                    notifications;
    private final transient AuthContextService                     authContextService;
    private final transient ComponentFactory<OverlayLayout>        overlayLayoutFactory;
    private final transient ComponentFactory<AuditUiPort>          auditUiPortFactory;
    private final transient ComponentFactory<UiPrimaryButton>      primaryButtonFactory;
    private final transient ComponentFactory<UiIconButton>         iconButtonFactory;
    private final transient ComponentFactory<AuditPort>            auditPortFactory;
    private final transient ComponentFactory<ConfirmActionDialog>  confirmDialogFactory;
    private final OverlayBreadcrumbBackButton breadcrumbBackButton;

    private OverlayLayout    layout;
    private IntegerField     adsPageSizeField;
    private IntegerField     usersPageSizeField;
    private Div              historyPanel;
    private Div              timelinePanel;
    private Tabs             tabs;
    private Tab              settingsTab;
    private UiPrimaryButton  saveBtn;

    public void openSettings() {
        authContextService.getCurrentUser().ifPresent(this::doOpen);
    }

    private void doOpen(User user) {
        ensureInitialized();

        if (layout != null) layout.removeFromParent();
        layout = overlayLayoutFactory.get();
        layout.setBreadcrumbButton(breadcrumbBackButton);
        layout.getBreadcrumbCurrent().setText(getValue(SETTINGS_SECTION_TITLE));

        UserSettings currentSettings = settingsService.load(user.getId());

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.add(buildAdsPageSizeField(currentSettings), buildUsersPageSizeField(currentSettings));

        Div settingsCardHeader = new Div(VaadinIcon.COG.create(), new Span(getValue(SETTINGS_SECTION_TITLE)));
        settingsCardHeader.addClassName("overlay__form-card-header");

        Div settingsPanel = new Div(settingsCardHeader, form);
        settingsPanel.addClassName("overlay__form-fields-card");

        Div content = auditUiPortFactory.findIfAvailable()
                .map(auditUi -> {
                    historyPanel  = new Div();
                    historyPanel.setVisible(false);
                    timelinePanel = new Div();
                    timelinePanel.setVisible(false);

                    settingsTab          = new Tab(getValue(SETTINGS_SECTION_TITLE));
                    Tab historyTab       = new Tab(getValue(SETTINGS_ACTIVITY_TAB));
                    Tab timelineTab      = new Tab(getValue(TIMELINE_TAB));
                    tabs = new Tabs(settingsTab, historyTab, timelineTab);
                    tabs.addClassName("user-view-tabs");

                    tabs.addSelectedChangeListener(event -> {
                        Tab selected    = event.getSelectedTab();
                        boolean isSettings  = selected == settingsTab;
                        boolean isHistory   = selected == historyTab;
                        settingsPanel.setVisible(isSettings);
                        historyPanel.setVisible(isHistory);
                        timelinePanel.setVisible(!isSettings && !isHistory);
                        if (isHistory && historyPanel.getChildren().findFirst().isEmpty()) {
                            historyPanel.add(buildHistoryContent(auditUi, user));
                        }
                        if (!isSettings && !isHistory && timelinePanel.getChildren().findFirst().isEmpty()) {
                            timelinePanel.add(buildTimelineContent(auditUi, user));
                        }
                    });

                    return new Div(tabs, settingsPanel, historyPanel, timelinePanel);
                })
                .orElseGet(() -> new Div(settingsPanel));

        content.addClassName("settings-overlay-content");
        layout.setContent(content);

        saveBtn = primaryButtonFactory.build(
                UiPrimaryButton.Parameters.builder().labelKey(SETTINGS_SAVE_BUTTON).build());
        saveBtn.addClickListener(_ -> handleSave(user));

        UiIconButton closeBtn = iconButtonFactory.build(
                UiIconButton.Parameters.builder()
                        .labelKey(HEADER_HOME)
                        .icon(VaadinIcon.CLOSE.create())
                        .build());
        closeBtn.addClickListener(_ -> closeToList());

        layout.setHeaderActions(new Div(saveBtn, closeBtn));

        add(layout);
        open();
    }

    @Override
    protected void buildContent() {
        addClassName("settings-overlay");
        breadcrumbBackButton.configure(OverlayBreadcrumbBackButton.Parameters.builder()
                        .labelKey(HEADER_HOME)
                        .build())
                .addClickListener(_ -> closeToList());
    }

    @Override
    protected void onEsc() {
        closeToList();
    }

    private void handleSave(User user) {
        if (saveBtn != null) saveBtn.setEnabled(false);
        try {
            UserSettings oldSettings = settingsService.load(user.getId());
            UserSettings newSettings = UserSettings.builder()
                    .adsPageSize(adsPageSizeField.getValue()     != null ? adsPageSizeField.getValue()   : PaginationDefaults.DEFAULT_PAGE_SIZE)
                    .usersPageSize(usersPageSizeField.getValue() != null ? usersPageSizeField.getValue() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                    .build();

            settingsService.save(user.getId(), newSettings);
            auditPortFactory.ifAvailable(p -> p.captureUpdate(user.getId(),
                    SettingsSnapshotDto.from(oldSettings),
                    SettingsSnapshotDto.from(newSettings),
                    user.getId()));
            if (historyPanel  != null) historyPanel.removeAll();
            if (timelinePanel != null) timelinePanel.removeAll();
            if (tabs != null) tabs.setSelectedTab(settingsTab);

            notifications.success(SETTINGS_SAVED_SUCCESS);
        } catch (Exception e) {
            notifications.error(e.getMessage());
        } finally {
            if (saveBtn != null) saveBtn.setEnabled(true);
        }
    }

    private com.vaadin.flow.component.Component buildHistoryContent(AuditUiPort auditUi, User user) {
        return auditUi.buildAuditActivityPanel(AuditUiPort.EntityActivityParams.builder()
                .entityType(EntityType.USER_SETTINGS)
                .entityId(user.getId())
                .userId(user.getId())
                .isPrivileged(true)
                .canOperate(true)
                .onRestoreRequested((item, entityId) -> loadAndShowSettingsRestore(item.snapshotId(), user))
                .build());
    }

    private com.vaadin.flow.component.Component buildTimelineContent(AuditUiPort auditUi, User user) {
        return auditUi.buildAuditTimelinePanel(AuditUiPort.TimelineParams.builder()
                .actorId(user.getId())
                .viewerActorId(user.getId())
                .build());
    }

    private void loadAndShowSettingsRestore(Long snapshotId, User user) {
        auditPortFactory.findIfAvailable()
                .flatMap(p -> p.<SettingsSnapshotDto>getSnapshotContent(snapshotId, EntityType.USER_SETTINGS))
                .map(c -> UserSettings.builder().adsPageSize(c.snapshotData().adsPageSize()).usersPageSize(c.snapshotData().usersPageSize()).build())
                .ifPresent(target -> showSettingsRestoreConfirm(target, user));
    }

    private void showSettingsRestoreConfirm(UserSettings target, User user) {
        UserSettings current = settingsService.load(user.getId());
        String noChange = getValue(ADVERTISEMENT_RESTORE_NO_CHANGE);

        String adsLabel = i18nService.get("audit.changes.setting.adsPageSize");
        String adsLine  = current.getAdsPageSize() == target.getAdsPageSize()
                ? adsLabel + ": " + noChange
                : adsLabel + ": " + current.getAdsPageSize() + " → " + target.getAdsPageSize();

        String usersLabel = i18nService.get("audit.changes.setting.usersPageSize");
        String usersLine  = current.getUsersPageSize() == target.getUsersPageSize()
                ? usersLabel + ": " + noChange
                : usersLabel + ": " + current.getUsersPageSize() + " → " + target.getUsersPageSize();

        confirmDialogFactory.build(
                ConfirmActionDialog.Parameters.builder()
                        .titleKey(SETTINGS_RESTORE_CONFIRM_TITLE)
                        .message(adsLine + "\n" + usersLine)
                        .confirmKey(ADVERTISEMENT_RESTORE_CONFIRM_BUTTON)
                        .cancelKey(ADVERTISEMENT_RESTORE_CONFIRM_CANCEL)
                        .onConfirm(() -> {
                            UserSettings before = settingsService.load(user.getId());
                            settingsService.save(user.getId(), target);
                            auditPortFactory.ifAvailable(p -> p.captureUpdate(user.getId(),
                                    SettingsSnapshotDto.from(before),
                                    SettingsSnapshotDto.from(target),
                                    user.getId()));
                            adsPageSizeField.setValue(target.getAdsPageSize());
                            usersPageSizeField.setValue(target.getUsersPageSize());
                            if (historyPanel  != null) historyPanel.removeAll();
                            if (timelinePanel != null) timelinePanel.removeAll();
                            tabs.setSelectedTab(settingsTab);
                            notifications.success(SETTINGS_RESTORED_SUCCESS);
                        })
                        .build()
        ).open();
    }

    private IntegerField buildAdsPageSizeField(UserSettings settings) {
        adsPageSizeField = new IntegerField(getValue(SETTINGS_ADS_PAGE_SIZE_LABEL));
        adsPageSizeField.setMin(PaginationDefaults.MIN_PAGE_SIZE);
        adsPageSizeField.setMax(PaginationDefaults.MAX_PAGE_SIZE);
        adsPageSizeField.setStep(5);
        adsPageSizeField.setStepButtonsVisible(true);
        adsPageSizeField.setValue(settings.getAdsPageSize());
        adsPageSizeField.setWidthFull();
        return adsPageSizeField;
    }

    private IntegerField buildUsersPageSizeField(UserSettings settings) {
        usersPageSizeField = new IntegerField(getValue(SETTINGS_USERS_PAGE_SIZE_LABEL));
        usersPageSizeField.setMin(PaginationDefaults.MIN_PAGE_SIZE);
        usersPageSizeField.setMax(PaginationDefaults.MAX_PAGE_SIZE);
        usersPageSizeField.setStep(5);
        usersPageSizeField.setStepButtonsVisible(true);
        usersPageSizeField.setValue(settings.getUsersPageSize());
        usersPageSizeField.setWidthFull();
        return usersPageSizeField;
    }
}
