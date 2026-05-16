package org.ost.advertisement.audit.ui;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.core.config.UserSettings;
import org.ost.advertisement.audit.dto.AdvertisementHistoryDto;
import org.ost.advertisement.audit.dto.ActivityItemDto;
import org.ost.advertisement.core.model.ActionType;
import org.ost.advertisement.core.model.ChangeEntry;
import org.ost.advertisement.core.model.EntityType;
import org.ost.advertisement.core.spi.AdvertisementHistoryExtension;
import org.ost.advertisement.core.i18n.I18nService;
import org.ost.advertisement.core.i18n.InstantFormatter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;

@CssImport("./adv-history.css")
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ActivityRowRenderer {

    private static final String CSS_CHANGES = "user-activity-changes";

    private final I18nService                                  i18n;
    private final InstantFormatter                             formatter;
    private final ActivityPanel                                activityPanel;
    private final ObjectProvider<AdvertisementHistoryExtension> historyExtensionProvider;

    public static boolean isSettingChange(ActivityItemDto item) {
        return item.entityType() == EntityType.USER_SETTINGS
                || (item.entityType() == EntityType.USER
                    && item.changes().stream().anyMatch(e -> e instanceof ChangeEntry.SettingChange));
    }

    public Div buildRow(ActivityItemDto item, Long viewerUserId) {
        Div row = new Div();
        row.addClassName("user-activity-row");
        if (!item.entityExists()) row.addClassName("user-activity-row--deleted");

        boolean settingChange = isSettingChange(item);
        String typeLabel  = settingChange ? "SETTINGS"  : item.entityType().name();
        String typeCssKey = settingChange ? "settings"  : item.entityType().name().toLowerCase();

        Span action = new Span(i18n.get(formatActionKey(item.actionType())));
        action.addClassName("user-activity-action");

        Span type = new Span(typeLabel);
        type.addClassName("user-activity-type");
        type.addClassName("user-activity-type--" + typeCssKey);

        String nameText = resolveNameText(item);
        Span name = new Span(nameText);
        name.addClassName("user-activity-name");

        Span time = new Span(formatter.formatInstantHuman(item.createdAt()));
        time.addClassName("user-activity-time");

        row.add(action, type, name, time);

        Span editor = ActivityPanel.buildEditorBadge(item.changedByUserId(), item.changedByName(), viewerUserId);
        if (editor != null) row.add(editor);

        if (settingChange) {
            if (item.snapshotId() == null || item.snapshotId() <= 0) {
                row.add(activityPanel.buildChangesList(item.changes(), CSS_CHANGES));
            }
        } else if (item.entityType() == EntityType.ADVERTISEMENT) {
            row.add(buildAdvFieldsList(item));
        } else if (item.entityType() == EntityType.USER) {
            row.add(buildUserFieldsList(item));
        }

        return row;
    }

    public Div buildAdvFieldsList(ActivityItemDto item) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);
        AdvChanges c = categorizeAdvChanges(item.changes());

        if (c.title() != null) {
            addActivitySpan(container, activityPanel.format(c.title()), false);
        } else if (item.snapshotTitle() != null) {
            addActivitySpan(container, i18n.get(AuditMessages.CHANGES_FIELD_TITLE) + ": " + item.snapshotTitle(), true);
        }

        if (c.desc() != null) {
            addActivitySpan(container, activityPanel.format(c.desc()), false);
        } else if (item.snapshotDescription() != null) {
            String desc = item.snapshotDescription().length() > 60
                    ? item.snapshotDescription().substring(0, 60) + "…" : item.snapshotDescription();
            addActivitySpan(container, i18n.get(AuditMessages.CHANGES_FIELD_DESCRIPTION) + ": " + desc, true);
        }

        renderActivityMediaSection(container, item, c.media());
        c.others().forEach(oc -> addActivitySpan(container, activityPanel.format(oc), false));
        return container;
    }

    private void renderActivityMediaSection(Div container, ActivityItemDto item, List<ChangeEntry> mediaChanges) {
        if (mediaChanges.isEmpty() && item.snapshotId() != null) {
            AdvertisementHistoryExtension ext = historyExtensionProvider.getIfAvailable();
            String state = ext != null
                    ? ext.getMediaStateForAdvSnapshot(item.entityId(), item.snapshotId())
                    : null;
            String mediaText = (state != null && !state.isBlank()) ? state : "—";
            addActivitySpan(container, i18n.get(AuditMessages.CHANGES_PHOTOS) + ": " + mediaText, true);
        } else {
            mediaChanges.forEach(pc -> addActivitySpan(container, activityPanel.format(pc), false));
        }
    }

    public Div buildUserFieldsList(ActivityItemDto item) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);

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
            addActivitySpan(container, i18n.get(AuditMessages.CHANGES_FIELD_NAME) + ": " + item.displayName(), true);
        }

        if (emailChange != null) {
            addActivitySpan(container, activityPanel.format(emailChange), false);
        } else if (item.snapshotEmail() != null) {
            addActivitySpan(container, i18n.get(AuditMessages.CHANGES_FIELD_EMAIL) + ": " + item.snapshotEmail(), true);
        }

        if (roleChange != null) {
            addActivitySpan(container, activityPanel.format(roleChange), false);
        } else if (item.snapshotRole() != null) {
            addActivitySpan(container, i18n.get(AuditMessages.CHANGES_FIELD_ROLE) + ": " + item.snapshotRole(), true);
        }

        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityPanel.format(oc), false);

        return container;
    }

    public Div buildSettingsFieldsList(ActivityItemDto item, UserSettings snapSettings) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);

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
            addActivitySpan(container, i18n.get(AuditMessages.CHANGES_SETTING_ADS_PAGE_SIZE) + ": " + snapSettings.getAdsPageSize(), true);
        }

        if (usersChange != null) {
            addActivitySpan(container, activityPanel.format(usersChange), false);
        } else {
            addActivitySpan(container, i18n.get(AuditMessages.CHANGES_SETTING_USERS_PAGE_SIZE) + ": " + snapSettings.getUsersPageSize(), true);
        }

        for (ChangeEntry oc : otherChanges) addActivitySpan(container, activityPanel.format(oc), false);

        return container;
    }

    public Div buildAdvHistoryFieldsList(AdvertisementHistoryDto h, Long adId) {
        Div container = new Div();
        container.addClassName("adv-history-changes");
        AdvChanges c = categorizeAdvChanges(h.changes());

        if (c.title() != null) {
            addHistorySpan(container, activityPanel.format(c.title()), false);
        } else if (h.title() != null) {
            addHistorySpan(container, i18n.get(AuditMessages.CHANGES_FIELD_TITLE) + ": " + h.title(), true);
        }

        if (c.desc() != null) {
            addHistorySpan(container, activityPanel.format(c.desc()), false);
        } else if (h.description() != null) {
            String desc = h.description().length() > 60 ? h.description().substring(0, 60) + "…" : h.description();
            addHistorySpan(container, i18n.get(AuditMessages.CHANGES_FIELD_DESCRIPTION) + ": " + desc, true);
        }

        renderHistoryMediaSection(container, c.media(), adId, h.version());
        c.others().forEach(oc -> addHistorySpan(container, activityPanel.format(oc), false));
        return container;
    }

    private void renderHistoryMediaSection(Div container, List<ChangeEntry> mediaChanges, Long adId, int version) {
        if (mediaChanges.isEmpty()) {
            AdvertisementHistoryExtension ext = historyExtensionProvider.getIfAvailable();
            String state = ext != null ? ext.getMediaStateAtVersion(adId, version) : null;
            String mediaText = (state != null && !state.isBlank()) ? state : "—";
            addHistorySpan(container, i18n.get(AuditMessages.CHANGES_PHOTOS) + ": " + mediaText, true);
        } else {
            mediaChanges.forEach(pc -> addHistorySpan(container, activityPanel.format(pc), false));
        }
    }

    private String resolveNameText(ActivityItemDto item) {
        if (item.entityType() == EntityType.ADVERTISEMENT) {
            return item.changedByName() != null ? item.changedByName() : item.displayName();
        }
        return item.entityExists()
                ? item.displayName()
                : item.displayName() + " " + i18n.get(AuditMessages.ACTIVITY_ENTITY_DELETED);
    }

    private static AuditMessages formatActionKey(ActionType actionType) {
        return switch (actionType) {
            case CREATED -> AuditMessages.ACTIVITY_ACTION_CREATED;
            case UPDATED -> AuditMessages.ACTIVITY_ACTION_UPDATED;
            case DELETED -> AuditMessages.ACTIVITY_ACTION_DELETED;
        };
    }

    private record AdvChanges(ChangeEntry title, ChangeEntry desc,
                               List<ChangeEntry> media, List<ChangeEntry> others) {}

    private static AdvChanges categorizeAdvChanges(List<ChangeEntry> entries) {
        ChangeEntry titleChange = null;
        ChangeEntry descChange  = null;
        List<ChangeEntry> mediaChanges = new ArrayList<>();
        List<ChangeEntry> otherChanges = new ArrayList<>();
        for (ChangeEntry entry : entries) {
            switch (entry) {
                case ChangeEntry.FieldChange f when "title".equals(f.field())       -> titleChange = f;
                case ChangeEntry.FieldChange f when "description".equals(f.field()) -> descChange  = f;
                case ChangeEntry.GenericChange gc                                    -> mediaChanges.add(gc);
                default                                                              -> otherChanges.add(entry);
            }
        }
        return new AdvChanges(titleChange, descChange, mediaChanges, otherChanges);
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
