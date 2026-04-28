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
import org.ost.advertisement.dto.ActivityItemDto;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.entities.ActionType;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.model.ChangeEntry;
import org.ost.advertisement.services.ActivityService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.SnapshotService;
import org.ost.advertisement.services.UserSettingsService;
import org.ost.advertisement.services.auth.AuthContextService;
import org.ost.advertisement.ui.views.components.buttons.UiIconButton;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.overlay.BaseOverlay;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.services.NotificationService;
import org.ost.advertisement.ui.views.utils.ActivityUiUtil;
import org.ost.advertisement.ui.views.utils.TimeZoneUtil;
import org.springframework.beans.factory.ObjectProvider;

import org.ost.advertisement.common.PaginationDefaults;

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
    private final OverlayBreadcrumbBackButton breadcrumbBackButton;
    private final transient UiPrimaryButton.Builder saveButtonBuilder;
    private final transient UiIconButton.Builder    closeButtonBuilder;

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
        UserSettings currentSettings = settingsService.load(userId);
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
                row.add(buildFullSettingsFieldsList(item, currentSettings));
            } else if ("USER".equals(item.entityType())) {
                row.add(buildFullUserFieldsList(item));
            } else if ("ADVERTISEMENT".equals(item.entityType())) {
                row.add(buildFullAdvFieldsList(item));
            }

            // Restore button for settings entries (USER type with a snapshot)
            if ("USER".equals(item.entityType()) && item.snapshotId() != null && item.snapshotId() > 0) {
                snapshotService.getSettingsFromSnapshot(item.snapshotId()).ifPresent(settings -> {
                    UserSettings live = settingsService.load(currentUser.getId());
                    boolean matchesCurrent = live.getAdsPageSize() == settings.getAdsPageSize()
                            && live.getUsersPageSize() == settings.getUsersPageSize();
                    if (matchesCurrent) {
                        Span badge = new Span(getValue(USER_ACTIVITY_CURRENT_STATE));
                        badge.addClassName("user-activity-current-badge");
                        row.add(badge);
                    } else {
                        Button restoreBtn = new Button(getValue(SETTINGS_RESTORE_BUTTON));
                        restoreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        restoreBtn.addClassName("adv-history-restore-btn");
                        restoreBtn.addClickListener(_ -> {
                            UserSettings current = settingsService.load(currentUser.getId());
                            settingsService.save(currentUser.getId(), settings);
                            snapshotService.captureSettingsChange(currentUser, current, settings, currentUser.getId());
                            adsPageSizeField.setValue(settings.getAdsPageSize());
                            usersPageSizeField.setValue(settings.getUsersPageSize());
                            activityPanel.removeAll();
                            tabs.setSelectedTab(settingsTab);
                            notifications.success(SETTINGS_RESTORED_SUCCESS);
                        });
                        row.add(restoreBtn);
                    }
                });
            }

            container.add(row);
        }
        return container;
    }

    private Div buildFullUserFieldsList(ActivityItemDto item) {
        Div container = new Div();
        container.addClassName("user-activity-changes");

        boolean nameInChanges  = false;
        boolean emailInChanges = false;
        boolean roleInChanges  = false;

        for (ChangeEntry entry : item.changes()) {
            switch (entry) {
                case ChangeEntry.FieldChange f when "name".equals(f.field())  -> nameInChanges  = true;
                case ChangeEntry.FieldChange f when "email".equals(f.field()) -> emailInChanges = true;
                case ChangeEntry.FieldChange f when "role".equals(f.field())  -> roleInChanges  = true;
                default -> {}
            }
            String text = activityUiUtil.format(entry);
            if (text != null && !text.isBlank()) {
                Span span = new Span("• " + text);
                span.addClassName("user-activity-changes-item");
                container.add(span);
            }
        }

        if (!nameInChanges && currentUser.getName() != null) {
            Span span = new Span("• " + getI18nService().get("changes.field.name") + ": " + currentUser.getName());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        if (!emailInChanges && currentUser.getEmail() != null) {
            Span span = new Span("• " + getI18nService().get("changes.field.email") + ": " + currentUser.getEmail());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        if (!roleInChanges && currentUser.getRole() != null) {
            Span span = new Span("• " + getI18nService().get("changes.field.role") + ": " + currentUser.getRole().name());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        return container;
    }

    private Div buildFullSettingsFieldsList(ActivityItemDto item, UserSettings currentSettings) {
        Div container = new Div();
        container.addClassName("user-activity-changes");

        boolean adsPageSizeInChanges   = false;
        boolean usersPageSizeInChanges = false;

        for (ChangeEntry entry : item.changes()) {
            switch (entry) {
                case ChangeEntry.SettingChange s when "adsPageSize".equals(s.key())   -> adsPageSizeInChanges   = true;
                case ChangeEntry.SettingChange s when "usersPageSize".equals(s.key()) -> usersPageSizeInChanges = true;
                default -> {}
            }
            String text = activityUiUtil.format(entry);
            if (text != null && !text.isBlank()) {
                Span span = new Span("• " + text);
                span.addClassName("user-activity-changes-item");
                container.add(span);
            }
        }

        if (!adsPageSizeInChanges) {
            Span span = new Span("• " + getI18nService().get("changes.setting.adsPageSize") + ": " + currentSettings.getAdsPageSize());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        if (!usersPageSizeInChanges) {
            Span span = new Span("• " + getI18nService().get("changes.setting.usersPageSize") + ": " + currentSettings.getUsersPageSize());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        return container;
    }

    private Div buildFullAdvFieldsList(ActivityItemDto item) {
        Div container = new Div();
        container.addClassName("user-activity-changes");

        boolean titleInChanges = false;
        boolean descInChanges  = false;
        boolean photoInChanges = false;

        for (ChangeEntry entry : item.changes()) {
            switch (entry) {
                case ChangeEntry.FieldChange f when "title".equals(f.field())       -> titleInChanges = true;
                case ChangeEntry.FieldChange f when "description".equals(f.field()) -> descInChanges  = true;
                case ChangeEntry.PhotoChange ignored                                 -> photoInChanges = true;
                default -> {}
            }
            String text = activityUiUtil.format(entry);
            if (text != null && !text.isBlank()) {
                Span span = new Span("• " + text);
                span.addClassName("user-activity-changes-item");
                container.add(span);
            }
        }

        if (!titleInChanges && item.snapshotTitle() != null) {
            Span span = new Span("• " + getValue(CHANGES_FIELD_TITLE) + ": " + item.snapshotTitle());
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        if (!descInChanges && item.snapshotDescription() != null) {
            String desc = item.snapshotDescription().length() > 60
                    ? item.snapshotDescription().substring(0, 60) + "…" : item.snapshotDescription();
            Span span = new Span("• " + getValue(CHANGES_FIELD_DESCRIPTION) + ": " + desc);
            span.addClassNames("user-activity-changes-item", "user-activity-changes-item--unchanged");
            container.add(span);
        }
        return container;
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
