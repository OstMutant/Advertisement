package org.ost.audit.ui;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotPayloadDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.codec.SnapshotCodec;
import org.ost.platform.audit.spi.ActivityFieldsHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.InstantFormatter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CssImport("./entity-history.css")
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ActivityRowRenderer {

    private static final String CSS_CHANGES         = "activity-feed-changes";
    private static final String CSS_HISTORY_CHANGES = "entity-history-changes";

    private final I18nService                              i18n;
    private final InstantFormatter                         formatter;
    private final ActivityPanel                            activityPanel;
    private final List<ActivityFieldsHook>              fieldsProviders;
    private final ObjectProvider<AttachmentAuditHook>   historyExtensionProvider;
    private final SnapshotCodec                            snapshotCodec;

    public Div buildRow(ActivityItemDto item, Long viewerActorId) {
        Div row = new Div();
        row.addClassName("activity-feed-row");
        if (!item.entityExists()) row.addClassName("activity-feed-row--deleted");

        String typeLabel  = item.entityType().name();
        String typeCssKey = item.entityType().name().toLowerCase();

        Span action = new Span(i18n.get(formatActionKey(item.actionType())));
        action.addClassName("activity-feed-action");
        action.addClassName("activity-feed-action--" + item.actionType().name().toLowerCase());

        Span type = new Span(typeLabel);
        type.addClassName("activity-feed-type");
        type.addClassName("activity-feed-type--" + typeCssKey);

        Span name = new Span(item.displayName());
        name.addClassName("activity-feed-name");

        Span time = new Span(formatter.formatInstantHuman(item.createdAt()));
        time.addClassName("activity-feed-time");

        row.add(action, type, name, time);

        Span editor = ActivityPanel.buildEditorBadge(item.changedByActorId(), item.changedByName(), viewerActorId);
        if (editor != null) row.add(editor);

        row.add(buildActivityFieldsList(item));
        return row;
    }

    private Div buildActivityFieldsList(ActivityItemDto item) {
        if (item.entityType() == EntityType.ADVERTISEMENT) {
            return buildAdvertisementActivityFieldsList(item);
        }
        ActivityFieldsHook provider = fieldsProviders.stream()
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
            addSpan(container, activityPanel.format(entry), unchanged, CSS_CHANGES);
        }

        if (mediaChanges.isEmpty()) {
            AttachmentAuditHook ext = historyExtensionProvider.getIfAvailable();
            String state = ext != null ? ext.getMediaStateForSnapshot(new EntityRef(item.entityType(), item.entityId()), item.snapshotId()) : null;
            String mediaText = (state != null && !state.isBlank()) ? state : "—";
            addSpan(container, i18n.get(AuditMessages.CHANGES_PHOTOS) + ": " + mediaText, true, CSS_CHANGES);
        } else {
            mediaChanges.forEach(pc -> addSpan(container, activityPanel.format(pc), false, CSS_CHANGES));
        }

        return container;
    }

    private Div buildActivityChangesDiv(List<ChangeEntry> entries) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);
        for (ChangeEntry entry : entries) {
            boolean unchanged = switch (entry) {
                case ChangeEntry.FieldChange fc   -> fc.from() == null || fc.from().isBlank();
                case ChangeEntry.SettingChange sc -> sc.from() == null;
                default                           -> false;
            };
            String text = activityPanel.format(entry);
            addSpan(container, text, unchanged, CSS_CHANGES);
        }
        return container;
    }

    public Div buildAdvHistoryFieldsList(EntityHistoryDto h, EntityType entityType, Long entityId) {
        Div container = new Div();
        container.addClassName("entity-history-changes");

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
            addSpan(container, activityPanel.format(entry), unchanged, CSS_HISTORY_CHANGES);
        }

        renderHistoryMediaSection(container, mediaChanges, entityType, entityId, h.version());
        return container;
    }

    private void renderHistoryMediaSection(Div container, List<ChangeEntry> mediaChanges,
                                           EntityType entityType, Long entityId, int version) {
        if (mediaChanges.isEmpty()) {
            AttachmentAuditHook ext = historyExtensionProvider.getIfAvailable();
            String state = ext != null ? ext.getMediaStateAtVersion(new EntityRef(entityType, entityId), version) : null;
            String mediaText = (state != null && !state.isBlank()) ? state : "—";
            addSpan(container, i18n.get(AuditMessages.CHANGES_PHOTOS) + ": " + mediaText, true, CSS_HISTORY_CHANGES);
        } else {
            mediaChanges.forEach(pc -> addSpan(container, activityPanel.format(pc), false, CSS_HISTORY_CHANGES));
        }
    }

    private List<ChangeEntry> expandTextFields(SnapshotPayloadDto payload, List<ChangeEntry> changedFields) {
        return snapshotCodec.decodeToMap(payload)
                .map(snap -> {
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
                })
                .orElse(changedFields);
    }

    private static AuditMessages formatActionKey(ActionType actionType) {
        return switch (actionType) {
            case CREATED -> AuditMessages.ACTIVITY_ACTION_CREATED;
            case UPDATED -> AuditMessages.ACTIVITY_ACTION_UPDATED;
            case DELETED -> AuditMessages.ACTIVITY_ACTION_DELETED;
        };
    }

    private static void addSpan(Div container, String text, boolean unchanged, String cssBase) {
        if (text == null || text.isBlank()) return;
        Span span = new Span("• " + text);
        span.addClassName(cssBase + "-item");
        if (unchanged) span.addClassName(cssBase + "-item--unchanged");
        container.add(span);
    }
}
