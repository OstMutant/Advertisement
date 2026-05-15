package org.ost.advertisement.ui.views.main.header.settings;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import org.ost.advertisement.audit.AuditPort;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.audit.SettingsSnapshot;
import org.ost.advertisement.services.user.UserSettingsService;
import org.ost.advertisement.services.auth.AuthContextService;
import org.ost.advertisement.ui.views.components.buttons.UiIconButton;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.advertisement.ui.views.components.overlay.BaseOverlay;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.advertisement.ui.rules.I18nParams;
import org.ost.advertisement.ui.views.services.NotificationService;
import org.ost.advertisement.events.spi.AuditUiExtension;
import org.springframework.beans.factory.ObjectProvider;

import org.ost.advertisement.common.PaginationDefaults;

import static org.ost.advertisement.common.I18nKey.*;

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
    private final transient AuditPort                              auditPort;

    private final transient ObjectProvider<OverlayLayout>                  layoutProvider;
    private final transient ObjectProvider<AuditUiExtension>               auditUiExtensionProvider;
    private final OverlayBreadcrumbBackButton breadcrumbBackButton;
    private final transient UiPrimaryButton.Builder    saveButtonBuilder;
    private final transient UiIconButton.Builder       closeButtonBuilder;
    private final transient ConfirmActionDialog.Builder confirmDialogBuilder;

    private OverlayLayout    layout;
    private IntegerField     adsPageSizeField;
    private IntegerField     usersPageSizeField;
    private Div              activityPanel;
    private Tabs             tabs;
    private Tab              settingsTab;
    private UiPrimaryButton  saveBtn;
    private transient User   currentUser;

    public void openSettings() {
        currentUser = authContextService.getCurrentUser().orElse(null);
        if (currentUser == null) return;

        ensureInitialized();

        if (layout != null) layout.removeFromParent();
        layout = layoutProvider.getObject();
        layout.setBreadcrumbButton(breadcrumbBackButton);
        layout.getBreadcrumbCurrent().setText(getValue(SETTINGS_SECTION_TITLE));

        UserSettings currentSettings = settingsService.load(currentUser.getId());

        // ── Settings panel ────────────────────────────────────────────────
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.add(buildAdsPageSizeField(currentSettings), buildUsersPageSizeField(currentSettings));

        Div settingsCardHeader = new Div(VaadinIcon.COG.create(), new Span(getValue(SETTINGS_SECTION_TITLE)));
        settingsCardHeader.addClassName("overlay__form-card-header");

        Div settingsPanel = new Div(settingsCardHeader, form);
        settingsPanel.addClassName("overlay__form-fields-card");

        Div content;

        AuditUiExtension auditUi = auditUiExtensionProvider.getIfAvailable();
        if (auditUi != null) {
            // ── Activity panel (lazy) ─────────────────────────────────────────
            activityPanel = new Div();
            activityPanel.setVisible(false);

            // ── Tabs ──────────────────────────────────────────────────────────
            settingsTab      = new Tab(getValue(SETTINGS_SECTION_TITLE));
            Tab activityTab  = new Tab(getValue(ACTIVITY_TAB));
            tabs = new Tabs(settingsTab, activityTab);
            tabs.addClassName("user-view-tabs");

            tabs.addSelectedChangeListener(event -> {
                boolean isSettings = event.getSelectedTab() == settingsTab;
                settingsPanel.setVisible(isSettings);
                activityPanel.setVisible(!isSettings);
                if (!isSettings && activityPanel.getChildren().findFirst().isEmpty()) {
                    activityPanel.add(buildActivityContent(currentUser.getId(), auditUi));
                }
            });

            content = new Div(tabs, settingsPanel, activityPanel);
        } else {
            content = new Div(settingsPanel);
        }

        content.addClassName("settings-overlay-content");
        layout.setContent(content);

        saveBtn = saveButtonBuilder.build(
                UiPrimaryButton.Parameters.builder().labelKey(SETTINGS_SAVE_BUTTON).build());
        saveBtn.addClickListener(_ -> handleSave());

        UiIconButton closeBtn = closeButtonBuilder.build(
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

    private void handleSave() {
        if (currentUser == null) return;
        if (saveBtn != null) saveBtn.setEnabled(false);
        try {
            UserSettings oldSettings = settingsService.load(currentUser.getId());
            UserSettings newSettings = UserSettings.builder()
                    .adsPageSize(adsPageSizeField.getValue()     != null ? adsPageSizeField.getValue()   : PaginationDefaults.DEFAULT_PAGE_SIZE)
                    .usersPageSize(usersPageSizeField.getValue() != null ? usersPageSizeField.getValue() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                    .build();

            settingsService.save(currentUser.getId(), newSettings);
            auditPort.captureUpdate(currentUser.getId(),
                    SettingsSnapshot.from(oldSettings),
                    SettingsSnapshot.from(newSettings),
                    currentUser.getId());
            if (activityPanel != null) activityPanel.removeAll();

            notifications.success(SETTINGS_SAVED_SUCCESS);
        } catch (Exception e) {
            notifications.error(e.getMessage());
        } finally {
            if (saveBtn != null) saveBtn.setEnabled(true);
        }
    }

    private com.vaadin.flow.component.Component buildActivityContent(Long userId, AuditUiExtension auditUi) {
        UserSettings current = settingsService.load(userId);
        return auditUi.buildUserActivityPanel(AuditUiExtension.UserActivityParams.builder()
                .userId(currentUser.getId())
                .userName(currentUser.getName())
                .userRole(currentUser.getRole())
                .currentSettings(current)
                .onRestoreSettings(this::showSettingsRestoreConfirm)
                .build());
    }

    private void showSettingsRestoreConfirm(UserSettings target) {
        UserSettings current = settingsService.load(currentUser.getId());
        String noChange = getValue(ADVERTISEMENT_RESTORE_NO_CHANGE);

        String adsLabel = i18nService.get("changes.setting.adsPageSize");
        String adsLine  = current.getAdsPageSize() == target.getAdsPageSize()
                ? adsLabel + ": " + noChange
                : adsLabel + ": " + current.getAdsPageSize() + " → " + target.getAdsPageSize();

        String usersLabel = i18nService.get("changes.setting.usersPageSize");
        String usersLine  = current.getUsersPageSize() == target.getUsersPageSize()
                ? usersLabel + ": " + noChange
                : usersLabel + ": " + current.getUsersPageSize() + " → " + target.getUsersPageSize();

        confirmDialogBuilder.build(
                ConfirmActionDialog.Parameters.builder()
                        .titleKey(SETTINGS_RESTORE_CONFIRM_TITLE)
                        .message(adsLine + "\n" + usersLine)
                        .confirmKey(ADVERTISEMENT_RESTORE_CONFIRM_BUTTON)
                        .cancelKey(ADVERTISEMENT_RESTORE_CONFIRM_CANCEL)
                        .onConfirm(() -> {
                            UserSettings before = settingsService.load(currentUser.getId());
                            settingsService.save(currentUser.getId(), target);
                            auditPort.captureUpdate(currentUser.getId(),
                                    SettingsSnapshot.from(before),
                                    SettingsSnapshot.from(target),
                                    currentUser.getId());
                            adsPageSizeField.setValue(target.getAdsPageSize());
                            usersPageSizeField.setValue(target.getUsersPageSize());
                            if (activityPanel != null) activityPanel.removeAll();
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
