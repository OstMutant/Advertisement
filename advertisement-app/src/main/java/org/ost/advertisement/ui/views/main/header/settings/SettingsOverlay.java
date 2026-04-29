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
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.services.ActivityService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.SnapshotService;
import org.ost.advertisement.services.UserSettingsService;
import org.ost.advertisement.services.auth.AuthContextService;
import org.ost.advertisement.ui.views.components.buttons.UiIconButton;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.advertisement.ui.views.components.overlay.BaseOverlay;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.services.NotificationService;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.ost.advertisement.ui.views.utils.ActivityUiUtil;
import org.ost.advertisement.ui.views.utils.TimeZoneUtil;
import org.springframework.beans.factory.ObjectProvider;

import org.ost.advertisement.common.PaginationDefaults;

import java.util.ArrayList;
import java.util.List;

import static org.ost.advertisement.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class SettingsOverlay extends BaseOverlay implements I18nParams {

    @Getter
    private final transient I18nService i18nService;
    private final transient UserSettingsService      settingsService;
    private final transient NotificationService      notifications;
    private final transient AuthContextService       authContextService;
    private final transient ActivityService          activityService;
    private final transient SnapshotService          snapshotService;
    private final transient ActivityUiUtil           activityUiUtil;

    private final transient ObjectProvider<OverlayLayout> layoutProvider;
    private final transient ObjectProvider<AdvertisementHistoryExtension> historyExtensionProvider;
    private final OverlayBreadcrumbBackButton breadcrumbBackButton;
    private final transient UiPrimaryButton.Builder    saveButtonBuilder;
    private final transient UiIconButton.Builder       closeButtonBuilder;
    private final transient ConfirmActionDialog.Builder confirmDialogBuilder;

    private OverlayLayout layout;
    private IntegerField  adsPageSizeField;
    private IntegerField  usersPageSizeField;
    private Div           activityPanel;
    private Tabs          tabs;
    private Tab           settingsTab;
    private transient User currentUser;

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
                activityPanel.add(buildActivityContent(currentUser.getId()));
            }
        });

        Div content = new Div(tabs, settingsPanel, activityPanel);
        content.addClassName("settings-overlay-content");
        layout.setContent(content);

        UiPrimaryButton saveBtn = saveButtonBuilder.build(
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

        UserSettings oldSettings = settingsService.load(currentUser.getId());
        UserSettings newSettings = UserSettings.builder()
                .adsPageSize(adsPageSizeField.getValue()     != null ? adsPageSizeField.getValue()   : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .usersPageSize(usersPageSizeField.getValue() != null ? usersPageSizeField.getValue() : PaginationDefaults.DEFAULT_PAGE_SIZE)
                .build();

        settingsService.save(currentUser.getId(), newSettings);
        snapshotService.captureSettingsChange(currentUser, oldSettings, newSettings, currentUser.getId());
        activityPanel.removeAll(); // force refresh on next tab switch

        notifications.success(SETTINGS_SAVED_SUCCESS);
    }

    private Div buildActivityContent(Long userId) {
        List<ActivityItemDto> items = activityService.getForUser(userId);
        Div container = new Div();
        container.addClassName("user-activity-list");

        if (items.isEmpty()) {
            Span empty = new Span(getValue(ACTIVITY_EMPTY));
            empty.addClassName("user-activity-empty");
            container.add(empty);
            return container;
        }

        for (ActivityItemDto item : items) {
            Div row = new Div();
            row.addClassName("user-activity-row");
            if (!item.entityExists()) row.addClassName("user-activity-row--deleted");

            boolean isSettingChange = "USER".equals(item.entityType())
                    && item.changes().stream().anyMatch(e -> e instanceof ChangeEntry.SettingChange);

            Span action = new Span(formatAction(item.actionType()));
            action.addClassName("user-activity-action");

            String typeLabel   = isSettingChange ? "SETTINGS" : item.entityType();
            String typeCssKey  = isSettingChange ? "settings"  : item.entityType().toLowerCase();
            Span type = new Span(typeLabel);
            type.addClassName("user-activity-type");
            type.addClassName("user-activity-type--" + typeCssKey);

            String nameText;
            if ("ADVERTISEMENT".equals(item.entityType())) {
                nameText = item.changedByName() != null ? item.changedByName() : item.displayName();
            } else {
                nameText = item.entityExists()
                        ? item.displayName()
                        : item.displayName() + " " + getValue(ACTIVITY_ENTITY_DELETED);
            }
            Span name = new Span(nameText);
            name.addClassName("user-activity-name");

            Span time = new Span(TimeZoneUtil.formatInstantHuman(item.createdAt()));
            time.addClassName("user-activity-time");

            row.add(action, type, name, time);

            Span editor = ActivityUiUtil.buildEditorBadge(item.changedByUserId(), item.changedByName(), userId);
            if (editor != null) row.add(editor);

            if (isSettingChange) {
                if (item.snapshotId() != null && item.snapshotId() > 0) {
                    snapshotService.getSettingsFromSnapshot(item.snapshotId()).ifPresent(snapSettings -> {
                        row.add(buildFullSettingsFieldsList(item, snapSettings));
                        UserSettings live = settingsService.load(currentUser.getId());
                        boolean matchesCurrent = live.getAdsPageSize() == snapSettings.getAdsPageSize()
                                && live.getUsersPageSize() == snapSettings.getUsersPageSize();
                        if (matchesCurrent) {
                            Span badge = new Span(getValue(USER_ACTIVITY_CURRENT_STATE));
                            badge.addClassName("user-activity-current-badge");
                            row.add(badge);
                        } else {
                            Button restoreBtn = new Button(getValue(SETTINGS_RESTORE_BUTTON));
                            restoreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                            restoreBtn.addClassName("adv-history-restore-btn");
                            restoreBtn.addClickListener(_ -> showSettingsRestoreConfirm(snapSettings));
                            row.add(restoreBtn);
                        }
                    });
                } else {
                    row.add(activityUiUtil.buildChangesList(item.changes(), "user-activity-changes"));
                }
            } else if ("USER".equals(item.entityType())) {
                row.add(buildFullUserFieldsList(item));
            } else if ("ADVERTISEMENT".equals(item.entityType())) {
                row.add(buildFullAdvFieldsList(item));
            }

            container.add(row);
        }
        return container;
    }

    private Div buildFullUserFieldsList(ActivityItemDto item) {
        Div container = new Div();
        container.addClassName("user-activity-changes");

        ChangeEntry nameChange  = null;
        ChangeEntry emailChange = null;
        ChangeEntry roleChange  = null;
        List<ChangeEntry> otherChanges = new ArrayList<>();

        for (ChangeEntry entry : item.changes()) {
            switch (entry) {
                case ChangeEntry.FieldChange f when "name".equals(f.field())  -> nameChange  = f;
                case ChangeEntry.FieldChange f when "email".equals(f.field()) -> emailChange = f;
                case ChangeEntry.FieldChange f when "role".equals(f.field())  -> roleChange  = f;
                default                                                        -> otherChanges.add(entry);
            }
        }

        if (nameChange != null) {
            addActivitySpan(container, activityUiUtil.format(nameChange), false);
        } else if (item.displayName() != null) {
            addActivitySpan(container, getI18nService().get("changes.field.name") + ": " + item.displayName(), true);
        }

        if (emailChange != null) {
            addActivitySpan(container, activityUiUtil.format(emailChange), false);
        } else if (item.snapshotEmail() != null) {
            addActivitySpan(container, getI18nService().get("changes.field.email") + ": " + item.snapshotEmail(), true);
        }

        if (roleChange != null) {
            addActivitySpan(container, activityUiUtil.format(roleChange), false);
        } else if (item.snapshotRole() != null) {
            addActivitySpan(container, getI18nService().get("changes.field.role") + ": " + item.snapshotRole(), true);
        }

        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityUiUtil.format(oc), false);

        return container;
    }

    private Div buildFullSettingsFieldsList(ActivityItemDto item, UserSettings snapSettings) {
        Div container = new Div();
        container.addClassName("user-activity-changes");

        ChangeEntry adsChange   = null;
        ChangeEntry usersChange = null;
        List<ChangeEntry> otherChanges = new ArrayList<>();

        for (ChangeEntry entry : item.changes()) {
            switch (entry) {
                case ChangeEntry.SettingChange s when "adsPageSize".equals(s.key())   -> adsChange   = s;
                case ChangeEntry.SettingChange s when "usersPageSize".equals(s.key()) -> usersChange = s;
                default                                                                -> otherChanges.add(entry);
            }
        }

        if (adsChange != null) {
            addActivitySpan(container, activityUiUtil.format(adsChange), false);
        } else {
            addActivitySpan(container, getI18nService().get("changes.setting.adsPageSize") + ": " + snapSettings.getAdsPageSize(), true);
        }

        if (usersChange != null) {
            addActivitySpan(container, activityUiUtil.format(usersChange), false);
        } else {
            addActivitySpan(container, getI18nService().get("changes.setting.usersPageSize") + ": " + snapSettings.getUsersPageSize(), true);
        }

        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityUiUtil.format(oc), false);

        return container;
    }

    private Div buildFullAdvFieldsList(ActivityItemDto item) {
        Div container = new Div();
        container.addClassName("user-activity-changes");

        ChangeEntry titleChange = null;
        ChangeEntry descChange  = null;
        List<ChangeEntry> photoChanges = new ArrayList<>();
        List<ChangeEntry> otherChanges = new ArrayList<>();

        for (ChangeEntry entry : item.changes()) {
            switch (entry) {
                case ChangeEntry.FieldChange f when "title".equals(f.field())       -> titleChange = f;
                case ChangeEntry.FieldChange f when "description".equals(f.field()) -> descChange  = f;
                case ChangeEntry.GenericChange gc                                    -> photoChanges.add(gc);
                default                                                              -> otherChanges.add(entry);
            }
        }

        if (titleChange != null) {
            addActivitySpan(container, activityUiUtil.format(titleChange), false);
        } else if (item.snapshotTitle() != null) {
            addActivitySpan(container, getValue(CHANGES_FIELD_TITLE) + ": " + item.snapshotTitle(), true);
        }

        if (descChange != null) {
            addActivitySpan(container, activityUiUtil.format(descChange), false);
        } else if (item.snapshotDescription() != null) {
            String desc = item.snapshotDescription().length() > 60
                    ? item.snapshotDescription().substring(0, 60) + "…" : item.snapshotDescription();
            addActivitySpan(container, getValue(CHANGES_FIELD_DESCRIPTION) + ": " + desc, true);
        }

        if (photoChanges.isEmpty() && item.snapshotId() != null) {
            AdvertisementHistoryExtension ext = historyExtensionProvider.getIfAvailable();
            if (ext != null) {
                String state = ext.getPhotoStateForAdvSnapshot(item.entityId(), item.snapshotId());
                if (state != null && !state.isBlank()) {
                    addActivitySpan(container, getValue(CHANGES_PHOTOS) + ": " + state, true);
                }
            }
        } else {
            for (ChangeEntry pc : photoChanges) addActivitySpan(container, activityUiUtil.format(pc), false);
        }
        for (ChangeEntry oc : otherChanges)  addActivitySpan(container, activityUiUtil.format(oc), false);

        return container;
    }

    private void addActivitySpan(Div container, String text, boolean unchanged) {
        if (text == null || text.isBlank()) return;
        Span span = new Span("• " + text);
        span.addClassName("user-activity-changes-item");
        if (unchanged) span.addClassName("user-activity-changes-item--unchanged");
        container.add(span);
    }

    private void showSettingsRestoreConfirm(UserSettings target) {
        UserSettings current = settingsService.load(currentUser.getId());
        List<String> lines = new ArrayList<>();
        String noChange = getValue(ADVERTISEMENT_RESTORE_NO_CHANGE);

        String adsLabel = getI18nService().get("changes.setting.adsPageSize");
        lines.add(current.getAdsPageSize() == target.getAdsPageSize()
                ? adsLabel + ": " + noChange
                : adsLabel + ": " + current.getAdsPageSize() + " → " + target.getAdsPageSize());

        String usersLabel = getI18nService().get("changes.setting.usersPageSize");
        lines.add(current.getUsersPageSize() == target.getUsersPageSize()
                ? usersLabel + ": " + noChange
                : usersLabel + ": " + current.getUsersPageSize() + " → " + target.getUsersPageSize());

        confirmDialogBuilder.build(
                ConfirmActionDialog.Parameters.builder()
                        .titleKey(SETTINGS_RESTORE_CONFIRM_TITLE)
                        .message(String.join("\n", lines))
                        .confirmKey(ADVERTISEMENT_RESTORE_CONFIRM_BUTTON)
                        .cancelKey(ADVERTISEMENT_RESTORE_CONFIRM_CANCEL)
                        .onConfirm(() -> {
                            UserSettings before = settingsService.load(currentUser.getId());
                            settingsService.save(currentUser.getId(), target);
                            snapshotService.captureSettingsChange(currentUser, before, target, currentUser.getId());
                            adsPageSizeField.setValue(target.getAdsPageSize());
                            usersPageSizeField.setValue(target.getUsersPageSize());
                            activityPanel.removeAll();
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
