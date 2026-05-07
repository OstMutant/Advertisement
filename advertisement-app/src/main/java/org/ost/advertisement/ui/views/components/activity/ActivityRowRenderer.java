package org.ost.advertisement.ui.views.components.activity;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.utils.TimeZoneUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;

import static org.ost.advertisement.common.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ActivityRowRenderer implements I18nParams {

    @Getter private final I18nService                                    i18nService;
    private final        ActivityPanel                                    activityUiUtil;
    private final        ObjectProvider<AdvertisementHistoryExtension>    historyExtensionProvider;

    public static boolean isSettingChange(ActivityItemDto item) {
        return "USER_SETTINGS".equals(item.entityType())
                || ("USER".equals(item.entityType())
                    && item.changes().stream().anyMatch(e -> e instanceof ChangeEntry.SettingChange));
    }

    public Div buildRow(ActivityItemDto item, Long viewerUserId) {
        Div row = new Div();
        row.addClassName("user-activity-row");
        if (!item.entityExists()) row.addClassName("user-activity-row--deleted");

        boolean settingChange = isSettingChange(item);
        String typeLabel  = settingChange ? "SETTINGS"  : item.entityType();
        String typeCssKey = settingChange ? "settings"  : item.entityType().toLowerCase();

        Span action = new Span(formatAction(item.actionType()));
        action.addClassName("user-activity-action");

        Span type = new Span(typeLabel);
        type.addClassName("user-activity-type");
        type.addClassName("user-activity-type--" + typeCssKey);

        String nameText = "ADVERTISEMENT".equals(item.entityType())
                ? (item.changedByName() != null ? item.changedByName() : item.displayName())
                : (item.entityExists()
                        ? item.displayName()
                        : item.displayName() + " " + getValue(ACTIVITY_ENTITY_DELETED));
        Span name = new Span(nameText);
        name.addClassName("user-activity-name");

        Span time = new Span(TimeZoneUtil.formatInstantHuman(item.createdAt()));
        time.addClassName("user-activity-time");

        row.add(action, type, name, time);

        Span editor = ActivityPanel.buildEditorBadge(item.changedByUserId(), item.changedByName(), viewerUserId);
        if (editor != null) row.add(editor);

        if (settingChange) {
            if (item.snapshotId() == null || item.snapshotId() <= 0) {
                row.add(activityUiUtil.buildChangesList(item.changes(), "user-activity-changes"));
            }
        } else if ("ADVERTISEMENT".equals(item.entityType())) {
            row.add(buildAdvFieldsList(item));
        } else if ("USER".equals(item.entityType())) {
            row.add(buildUserFieldsList(item));
        }

        return row;
    }

    public Div buildAdvFieldsList(ActivityItemDto item) {
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
            String state = ext != null
                    ? ext.getPhotoStateForAdvSnapshot(item.entityId(), item.snapshotId())
                    : null;
            String photoText = (state != null && !state.isBlank()) ? state : "—";
            addActivitySpan(container, getValue(CHANGES_PHOTOS) + ": " + photoText, true);
        } else {
            for (ChangeEntry pc : photoChanges) addActivitySpan(container, activityUiUtil.format(pc), false);
        }
        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityUiUtil.format(oc), false);

        return container;
    }

    public Div buildUserFieldsList(ActivityItemDto item) {
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
            addActivitySpan(container, i18nService.get("changes.field.name") + ": " + item.displayName(), true);
        }

        if (emailChange != null) {
            addActivitySpan(container, activityUiUtil.format(emailChange), false);
        } else if (item.snapshotEmail() != null) {
            addActivitySpan(container, i18nService.get("changes.field.email") + ": " + item.snapshotEmail(), true);
        }

        if (roleChange != null) {
            addActivitySpan(container, activityUiUtil.format(roleChange), false);
        } else if (item.snapshotRole() != null) {
            addActivitySpan(container, i18nService.get("changes.field.role") + ": " + item.snapshotRole(), true);
        }

        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityUiUtil.format(oc), false);

        return container;
    }

    public Div buildSettingsFieldsList(ActivityItemDto item, UserSettings snapSettings) {
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
            addActivitySpan(container, i18nService.get("changes.setting.adsPageSize") + ": " + snapSettings.getAdsPageSize(), true);
        }

        if (usersChange != null) {
            addActivitySpan(container, activityUiUtil.format(usersChange), false);
        } else {
            addActivitySpan(container, i18nService.get("changes.setting.usersPageSize") + ": " + snapSettings.getUsersPageSize(), true);
        }

        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityUiUtil.format(oc), false);

        return container;
    }

    public Div buildAdvHistoryFieldsList(AdvertisementHistoryDto h, Long adId) {
        Div container = new Div();
        container.addClassName("adv-history-changes");

        ChangeEntry titleChange = null;
        ChangeEntry descChange  = null;
        List<ChangeEntry> photoChanges = new ArrayList<>();
        List<ChangeEntry> otherChanges = new ArrayList<>();

        for (ChangeEntry entry : h.changes()) {
            switch (entry) {
                case ChangeEntry.FieldChange f when "title".equals(f.field())       -> titleChange = f;
                case ChangeEntry.FieldChange f when "description".equals(f.field()) -> descChange  = f;
                case ChangeEntry.GenericChange gc                                    -> photoChanges.add(gc);
                default                                                              -> otherChanges.add(entry);
            }
        }

        if (titleChange != null) {
            addHistorySpan(container, activityUiUtil.format(titleChange), false);
        } else if (h.title() != null) {
            addHistorySpan(container, getValue(CHANGES_FIELD_TITLE) + ": " + h.title(), true);
        }

        if (descChange != null) {
            addHistorySpan(container, activityUiUtil.format(descChange), false);
        } else if (h.description() != null) {
            String desc = h.description().length() > 60 ? h.description().substring(0, 60) + "…" : h.description();
            addHistorySpan(container, getValue(CHANGES_FIELD_DESCRIPTION) + ": " + desc, true);
        }

        if (photoChanges.isEmpty()) {
            AdvertisementHistoryExtension ext = historyExtensionProvider.getIfAvailable();
            String state = ext != null ? ext.getPhotoStateAtVersion(adId, h.version()) : null;
            String photoText = (state != null && !state.isBlank()) ? state : "—";
            addHistorySpan(container, getValue(CHANGES_PHOTOS) + ": " + photoText, true);
        } else {
            for (ChangeEntry pc : photoChanges) addHistorySpan(container, activityUiUtil.format(pc), false);
        }
        for (ChangeEntry oc : otherChanges) addHistorySpan(container, activityUiUtil.format(oc), false);

        return container;
    }

    private void addHistorySpan(Div container, String text, boolean unchanged) {
        if (text == null || text.isBlank()) return;
        Span span = new Span("• " + text);
        span.addClassName("adv-history-changes-item");
        if (unchanged) span.addClassName("adv-history-changes-item--unchanged");
        container.add(span);
    }

    private void addActivitySpan(Div container, String text, boolean unchanged) {
        if (text == null || text.isBlank()) return;
        Span span = new Span("• " + text);
        span.addClassName("user-activity-changes-item");
        if (unchanged) span.addClassName("user-activity-changes-item--unchanged");
        container.add(span);
    }
}
