package org.ost.advertisement.audit.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ActivityRowRenderer implements AuditI18nSupport {

    @Getter private final MessageSource                                  messageSource;
    private final        ActivityPanel                                   activityPanel;
    private final        ObjectProvider<AdvertisementHistoryExtension>   historyExtensionProvider;

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
                        : item.displayName() + " " + msg(AuditKeys.ACTIVITY_ENTITY_DELETED));
        Span name = new Span(nameText);
        name.addClassName("user-activity-name");

        Span time = new Span(formatInstantHuman(item.createdAt()));
        time.addClassName("user-activity-time");

        row.add(action, type, name, time);

        Span editor = ActivityPanel.buildEditorBadge(item.changedByUserId(), item.changedByName(), viewerUserId);
        if (editor != null) row.add(editor);

        if (settingChange) {
            if (item.snapshotId() == null || item.snapshotId() <= 0) {
                row.add(activityPanel.buildChangesList(item.changes(), "user-activity-changes"));
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
            addActivitySpan(container, activityPanel.format(titleChange), false);
        } else if (item.snapshotTitle() != null) {
            addActivitySpan(container, msg(AuditKeys.CHANGES_FIELD_TITLE) + ": " + item.snapshotTitle(), true);
        }

        if (descChange != null) {
            addActivitySpan(container, activityPanel.format(descChange), false);
        } else if (item.snapshotDescription() != null) {
            String desc = item.snapshotDescription().length() > 60
                    ? item.snapshotDescription().substring(0, 60) + "…" : item.snapshotDescription();
            addActivitySpan(container, msg(AuditKeys.CHANGES_FIELD_DESCRIPTION) + ": " + desc, true);
        }

        if (photoChanges.isEmpty() && item.snapshotId() != null) {
            AdvertisementHistoryExtension ext = historyExtensionProvider.getIfAvailable();
            String state = ext != null
                    ? ext.getMediaStateForAdvSnapshot(item.entityId(), item.snapshotId())
                    : null;
            String photoText = (state != null && !state.isBlank()) ? state : "—";
            addActivitySpan(container, msg(AuditKeys.CHANGES_PHOTOS) + ": " + photoText, true);
        } else {
            for (ChangeEntry pc : photoChanges) addActivitySpan(container, activityPanel.format(pc), false);
        }
        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityPanel.format(oc), false);

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
            addActivitySpan(container, activityPanel.format(nameChange), false);
        } else if (item.displayName() != null) {
            addActivitySpan(container, msg("changes.field.name") + ": " + item.displayName(), true);
        }

        if (emailChange != null) {
            addActivitySpan(container, activityPanel.format(emailChange), false);
        } else if (item.snapshotEmail() != null) {
            addActivitySpan(container, msg("changes.field.email") + ": " + item.snapshotEmail(), true);
        }

        if (roleChange != null) {
            addActivitySpan(container, activityPanel.format(roleChange), false);
        } else if (item.snapshotRole() != null) {
            addActivitySpan(container, msg("changes.field.role") + ": " + item.snapshotRole(), true);
        }

        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityPanel.format(oc), false);

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
            addActivitySpan(container, activityPanel.format(adsChange), false);
        } else {
            addActivitySpan(container, msg("changes.setting.adsPageSize") + ": " + snapSettings.getAdsPageSize(), true);
        }

        if (usersChange != null) {
            addActivitySpan(container, activityPanel.format(usersChange), false);
        } else {
            addActivitySpan(container, msg("changes.setting.usersPageSize") + ": " + snapSettings.getUsersPageSize(), true);
        }

        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityPanel.format(oc), false);

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
            addHistorySpan(container, activityPanel.format(titleChange), false);
        } else if (h.title() != null) {
            addHistorySpan(container, msg(AuditKeys.CHANGES_FIELD_TITLE) + ": " + h.title(), true);
        }

        if (descChange != null) {
            addHistorySpan(container, activityPanel.format(descChange), false);
        } else if (h.description() != null) {
            String desc = h.description().length() > 60 ? h.description().substring(0, 60) + "…" : h.description();
            addHistorySpan(container, msg(AuditKeys.CHANGES_FIELD_DESCRIPTION) + ": " + desc, true);
        }

        if (photoChanges.isEmpty()) {
            AdvertisementHistoryExtension ext = historyExtensionProvider.getIfAvailable();
            String state = ext != null ? ext.getMediaStateAtVersion(adId, h.version()) : null;
            String photoText = (state != null && !state.isBlank()) ? state : "—";
            addHistorySpan(container, msg(AuditKeys.CHANGES_PHOTOS) + ": " + photoText, true);
        } else {
            for (ChangeEntry pc : photoChanges) addHistorySpan(container, activityPanel.format(pc), false);
        }
        for (ChangeEntry oc : otherChanges) addHistorySpan(container, activityPanel.format(oc), false);

        return container;
    }

    // Inlined from TimeZoneUtil — intentional copy; TimeZoneUtil stays in advertisement-app.
    private static String formatInstantHuman(Instant instant) {
        if (instant == null) return "N/A";
        VaadinSession session = VaadinSession.getCurrent();
        String tzId = session != null ? (String) session.getAttribute("clientTimeZoneId") : null;
        ZoneId zone = (tzId != null) ? ZoneId.of(tzId) : ZoneId.systemDefault();
        Locale locale = (session != null && session.getLocale() != null) ? session.getLocale() : Locale.getDefault();
        return LocalDateTime.ofInstant(instant, zone)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withLocale(locale));
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
