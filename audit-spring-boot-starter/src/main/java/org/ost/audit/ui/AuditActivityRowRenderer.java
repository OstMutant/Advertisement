package org.ost.audit.ui;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.platform.audit.spi.AuditActivityRenderHook;
import org.ost.platform.audit.spi.AuditFieldLabelHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.InstantFormatter;
import org.springframework.context.annotation.Scope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@CssImport("./entity-history.css")
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditActivityRowRenderer {

    private static final String CSS_CHANGES         = "activity-feed-changes";
    private static final String CSS_HISTORY_CHANGES = "entity-history-changes";

    private final I18nService                              i18n;
    private final InstantFormatter                         formatter;
    private final AuditChangeFormatter                            activityPanel;
    private final List<AuditActivityFieldsHook>                 fieldsProviders;
    private final List<AuditActivityRenderHook>             renderStrategies;
    private final List<AuditFieldLabelHook>                 labelProviders;

    record DisplayContext(
            Map<Long, String> actorNames,
            Map<Long, String> displayNames,
            Set<EntityRef>    existingRefs) {}

    public Div buildRow(AuditActivityItemDto item, Long viewerActorId, DisplayContext ctx) {
        Div row = new Div();
        row.addClassName("activity-feed-row");
        if (!ctx.existingRefs().contains(new EntityRef(item.entityType(), item.entityId())))
            row.addClassName("activity-feed-row--deleted");

        row.add(actionSpan(item.actionType()), typeSpan(item.entityType().name()),
                nameSpan(ctx.displayNames().getOrDefault(item.entityId(), "")), timeSpan(item.createdAt()));

        String changedByName = item.changedByActorId() != null
                ? ctx.actorNames().getOrDefault(item.changedByActorId(), "") : null;
        Span editor = activityPanel.buildEditorBadge(item.changedByActorId(), changedByName, viewerActorId);
        if (editor != null) row.add(editor);

        row.add(buildActivityFieldsList(item));
        return row;
    }

    private Span actionSpan(ActionType actionType) {
        Span span = new Span(i18n.get(AuditI18n.forAction(actionType)));
        span.addClassName("activity-feed-action");
        span.addClassName("activity-feed-action--" + actionType.name().toLowerCase());
        return span;
    }

    private static Span typeSpan(String typeName) {
        Span span = new Span(typeName);
        span.addClassName("activity-feed-type");
        span.addClassName("activity-feed-type--" + typeName.toLowerCase());
        return span;
    }

    private static Span nameSpan(String displayName) {
        Span span = new Span(displayName);
        span.addClassName("activity-feed-name");
        return span;
    }

    private Span timeSpan(Instant createdAt) {
        Span span = new Span(formatter.formatInstantHuman(createdAt));
        span.addClassName("activity-feed-time");
        return span;
    }

    private Div buildActivityFieldsList(AuditActivityItemDto item) {
        AuditActivityRenderHook strategy = renderStrategies.stream()
                .filter(s -> s.entityType() == item.entityType())
                .findFirst().orElse(null);
        if (strategy != null) {
            EntityRef ref = new EntityRef(item.entityType(), item.entityId());
            return buildEntityChangesDiv(item.changes(), item.snapshotData(), CSS_CHANGES,
                    () -> strategy.getMediaStateForSnapshot(ref, item.snapshotId()));
        }
        AuditActivityFieldsHook provider = fieldsProviders.stream()
                .filter(p -> p.supports(item.entityType()))
                .findFirst().orElse(null);
        if (provider != null && item.snapshotData() != null) {
            AuditFieldLabelHook labelHook = labelProviders.stream()
                    .filter(h -> h.supports(item.entityType()))
                    .findFirst().orElse(null);
            return buildActivityChangesDiv(provider.expandFields(item), labelHook);
        }
        return activityPanel.buildChangesList(item.changes(), CSS_CHANGES);
    }

    private Div buildActivityChangesDiv(List<ChangeEntry> entries, AuditFieldLabelHook labelHook) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);
        for (ChangeEntry entry : entries) {
            ChangeEntry resolved = labelHook != null && entry instanceof ChangeEntry.FieldChange fc
                    ? new ChangeEntry.FieldChange(labelHook.labelFor(fc.field()), fc.from(), fc.to())
                    : entry;
            boolean unchanged = switch (resolved) {
                case ChangeEntry.FieldChange fc  -> fc.from() == null || fc.from().isBlank();
                case ChangeEntry.MediaChange mc  -> mc.before() == null || mc.before().isBlank();
            };
            addSpan(container, activityPanel.format(resolved), unchanged, CSS_CHANGES);
        }
        return container;
    }

    public Div buildHistoryFieldsList(AuditHistoryItemDto h, EntityRef ref) {
        AuditActivityRenderHook strategy = renderStrategies.stream()
                .filter(s -> s.entityType() == ref.entityType())
                .findFirst().orElse(null);
        Supplier<String> mediaLookup = strategy != null
                ? () -> strategy.getMediaStateAtVersion(ref, h.version())
                : null;
        return buildEntityChangesDiv(h.changes(), h.snapshotData(), CSS_HISTORY_CHANGES, mediaLookup);
    }

    private Div buildEntityChangesDiv(List<ChangeEntry> changes, AuditableSnapshot snapshotData,
                                      String cssBase, Supplier<String> mediaStateLookup) {
        Div container = new Div();
        container.addClassName(cssBase);

        List<ChangeEntry> mediaChanges = new ArrayList<>();
        List<ChangeEntry> textChanges  = new ArrayList<>();
        for (ChangeEntry entry : changes) {
            if (entry instanceof ChangeEntry.MediaChange) {
                mediaChanges.add(entry);
            } else {
                textChanges.add(entry);
            }
        }

        List<ChangeEntry> expanded = expandTextFields(snapshotData, textChanges);
        for (ChangeEntry entry : expanded) {
            boolean unchanged = entry instanceof ChangeEntry.FieldChange fc && (fc.from() == null || fc.from().isBlank());
            addSpan(container, activityPanel.format(entry), unchanged, cssBase);
        }

        if (!mediaChanges.isEmpty()) {
            mediaChanges.forEach(pc -> addSpan(container, activityPanel.format(pc), false, cssBase));
        } else if (mediaStateLookup != null) {
            String state     = mediaStateLookup.get();
            String mediaText = (state != null && !state.isBlank()) ? state : "—";
            addSpan(container, i18n.get(AuditI18n.CHANGES_SET, i18n.get(AuditI18n.CHANGES_MEDIA), mediaText), true, cssBase);
        }

        return container;
    }

    private List<ChangeEntry> expandTextFields(AuditableSnapshot snapshot, List<ChangeEntry> changedFields) {
        if (snapshot == null) return changedFields;
        return snapshot.allFields().stream()
                .map(base -> {
                    String key = ((ChangeEntry.FieldChange) base).field();
                    return changedFields.stream()
                            .filter(c -> c instanceof ChangeEntry.FieldChange fc && key.equals(fc.field()))
                            .findFirst()
                            .orElse(base);
                })
                .toList();
    }

    private void addSpan(Div container, String text, boolean unchanged, String cssBase) {
        if (text == null || text.isBlank()) return;
        Span span = new Span(i18n.get(AuditI18n.CHANGES_BULLET, text));
        span.addClassName(cssBase + "-item");
        if (unchanged) span.addClassName(cssBase + "-item--unchanged");
        container.add(span);
    }
}
