package org.ost.audit.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.config.UserSettings;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotPayload;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.ActivityItemFieldsProvider;
import org.ost.platform.core.spi.AdvertisementHistoryExtension;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.InstantFormatter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CssImport("./adv-history.css")
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ActivityRowRenderer {

    private static final String CSS_CHANGES = "user-activity-changes";

    private final I18nService                                    i18n;
    private final InstantFormatter                               formatter;
    private final ActivityPanel                                  activityPanel;
    private final List<ActivityItemFieldsProvider>               fieldsProviders;
    private final ObjectProvider<AdvertisementHistoryExtension>  historyExtensionProvider;
    private final ObjectMapper                                   objectMapper;

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

        Span name = new Span(item.displayName());
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
            // else: full fields list rendered by ProfileActivityPanel.addSettingsRestore
        } else {
            row.add(buildActivityFieldsList(item));
        }

        return row;
    }

    private Div buildActivityFieldsList(ActivityItemDto item) {
        if (item.entityType() == EntityType.ADVERTISEMENT) {
            return buildAdvertisementActivityFieldsList(item);
        }
        ActivityItemFieldsProvider provider = fieldsProviders.stream()
                .filter(p -> p.supports(item.entityType()))
                .findFirst().orElse(null);
        if (provider != null && !item.snapshotData().isEmpty()) {
            List<ChangeEntry> expanded = provider.expandFields(item);
            return buildActivityChangesDiv(expanded);
        }
        return activityPanel.buildChangesList(item.changes(), CSS_CHANGES);
    }

    private Div buildAdvertisementActivityFieldsList(ActivityItemDto item) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);

        List<ChangeEntry> mediaChanges = new ArrayList<>();
        List<ChangeEntry> textChanges  = new ArrayList<>();
        for (ChangeEntry entry : item.changes()) {
            if (entry instanceof ChangeEntry.GenericChange) {
                mediaChanges.add(entry);
            } else {
                textChanges.add(entry);
            }
        }

        List<ChangeEntry> expanded = expandTextFields(item.snapshotData(), textChanges);
        for (ChangeEntry entry : expanded) {
            boolean unchanged = entry instanceof ChangeEntry.FieldChange fc && (fc.from() == null || fc.from().isBlank());
            addActivitySpan(container, activityPanel.format(entry), unchanged);
        }

        if (mediaChanges.isEmpty()) {
            AdvertisementHistoryExtension ext = historyExtensionProvider.getIfAvailable();
            String state = ext != null ? ext.getMediaStateForAdvSnapshot(item.entityId(), item.snapshotId()) : null;
            String mediaText = (state != null && !state.isBlank()) ? state : "—";
            addActivitySpan(container, i18n.get(AuditMessages.CHANGES_PHOTOS) + ": " + mediaText, true);
        } else {
            mediaChanges.forEach(pc -> addActivitySpan(container, activityPanel.format(pc), false));
        }

        return container;
    }

    private Div buildActivityChangesDiv(List<ChangeEntry> entries) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);
        for (ChangeEntry entry : entries) {
            boolean unchanged = entry instanceof ChangeEntry.FieldChange fc && (fc.from() == null || fc.from().isBlank());
            String text = activityPanel.format(entry);
            addActivitySpan(container, text, unchanged);
        }
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

    public Div buildAdvHistoryFieldsList(EntityHistoryDto h, Long adId) {
        Div container = new Div();
        container.addClassName("adv-history-changes");

        List<ChangeEntry> mediaChanges = new ArrayList<>();
        List<ChangeEntry> textChanges  = new ArrayList<>();
        for (ChangeEntry entry : h.changes()) {
            if (entry instanceof ChangeEntry.GenericChange) {
                mediaChanges.add(entry);
            } else {
                textChanges.add(entry);
            }
        }

        List<ChangeEntry> expanded = expandTextFields(h.snapshotData(), textChanges);
        for (ChangeEntry entry : expanded) {
            boolean unchanged = entry instanceof ChangeEntry.FieldChange fc && (fc.from() == null || fc.from().isBlank());
            addHistorySpan(container, activityPanel.format(entry), unchanged);
        }

        renderHistoryMediaSection(container, mediaChanges, adId, h.version());
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

    @SuppressWarnings("unchecked")
    private List<ChangeEntry> expandTextFields(SnapshotPayload payload, List<ChangeEntry> changedFields) {
        if (payload == null || payload.isEmpty()) return changedFields;
        try {
            Map<String, Object> snap = objectMapper.readValue(payload.json(), Map.class);
            List<ChangeEntry> result = new ArrayList<>();
            for (Map.Entry<String, Object> e : snap.entrySet()) {
                String key = e.getKey();
                String val = e.getValue() != null ? String.valueOf(e.getValue()) : "";
                ChangeEntry existing = changedFields.stream()
                        .filter(c -> c instanceof ChangeEntry.FieldChange fc && key.equals(fc.field()))
                        .findFirst().orElse(null);
                result.add(existing != null ? existing : new ChangeEntry.FieldChange(key, null, val));
            }
            return result;
        } catch (Exception _) {
            return changedFields;
        }
    }

    private static AuditMessages formatActionKey(ActionType actionType) {
        return switch (actionType) {
            case CREATED -> AuditMessages.ACTIVITY_ACTION_CREATED;
            case UPDATED -> AuditMessages.ACTIVITY_ACTION_UPDATED;
            case DELETED -> AuditMessages.ACTIVITY_ACTION_DELETED;
        };
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
