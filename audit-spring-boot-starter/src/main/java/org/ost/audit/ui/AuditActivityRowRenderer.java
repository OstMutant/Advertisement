package org.ost.audit.ui;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.ui.Initialization;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.InstantFormatter;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.context.annotation.Scope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@CssImport("./entity-history.css")
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditActivityRowRenderer implements Initialization<AuditActivityRowRenderer> {

    private static final String CSS_CHANGES         = "activity-feed-changes";
    private static final String CSS_HISTORY_CHANGES = "entity-history-changes";

    private final I18nService                   i18n;
    private final InstantFormatter              formatter;
    private final AuditChangeFormatter          changeFormatter;
    private final List<AuditActivityFieldsHook> fieldsProviderList;
    private final List<AuditActivityEnrichHook> enrichHookList;

    private Map<EntityType, AuditActivityFieldsHook> fieldsProviders;
    private Map<EntityType, AuditActivityEnrichHook> enrichHooks;

    @Override
    @PostConstruct
    public AuditActivityRowRenderer init() {
        fieldsProviders = fieldsProviderList.stream().collect(Collectors.toMap(AuditActivityFieldsHook::entityType, h -> h, (a, _) -> a, () -> new EnumMap<>(EntityType.class)));
        enrichHooks     = enrichHookList.stream().collect(Collectors.toMap(AuditActivityEnrichHook::entityType, h -> h, (a, _) -> a, () -> new EnumMap<>(EntityType.class)));
        return this;
    }

    record RowContext(
            Map<Long, String> actorNames,
            Map<Long, String> displayNames,
            Set<EntityRef>    existingRefs) {}

    Div buildRow(AuditActivityItemDto<AuditableSnapshot> item, Long viewerActorId, RowContext ctx) {
        Div row = new Div();
        row.addClassName("activity-feed-row");
        if (!ctx.existingRefs().contains(new EntityRef(item.entityType(), item.entityId())))
            row.addClassName("activity-feed-row--deleted");

        row.add(actionSpan(item.actionType()), typeSpan(item.entityType().name()),
                nameSpan(ctx.displayNames().getOrDefault(item.entityId(), "")), timeSpan(item.createdAt()));

        String changedByName = item.changedByActorId() != null
                ? ctx.actorNames().getOrDefault(item.changedByActorId(), "") : null;
        Span editor = changeFormatter.buildEditorBadge(item.changedByActorId(), changedByName, viewerActorId);
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

    private Div buildActivityFieldsList(AuditActivityItemDto<AuditableSnapshot> item) {
        AuditActivityEnrichHook enrichHook = enrichHooks.get(item.entityType());
        if (enrichHook != null) {
            EntityRef ref = new EntityRef(item.entityType(), item.entityId());
            return buildEntityChangesDiv(item.changes(), item.snapshotData(), CSS_CHANGES,
                    () -> enrichHook.getMediaStateForSnapshot(ref, item.snapshotId()));
        }
        AuditActivityFieldsHook provider = fieldsProviders.get(item.entityType());
        if (provider != null && item.snapshotData() != null) {
            return buildActivityChangesDiv(provider.expandFields(item), provider);
        }
        return changeFormatter.buildChangesList(item.changes(), CSS_CHANGES);
    }

    private Div buildActivityChangesDiv(List<ChangeEntry> entries, AuditActivityFieldsHook labelHook) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);
        for (ChangeEntry entry : entries) {
            ChangeEntry resolved = switch (entry) {
                case ChangeEntry.FieldChange(var field, var from, var to) -> new ChangeEntry.FieldChange(labelHook.labelFor(field), from, to);
                case ChangeEntry.MediaChange _ -> entry;
            };
            boolean unchanged = switch (resolved) {
                case ChangeEntry.FieldChange(_, var from, _) -> from == null || from.isBlank();
                case ChangeEntry.MediaChange(var before, _)  -> before == null || before.isBlank();
            };
            addSpan(container, changeFormatter.format(resolved), unchanged, CSS_CHANGES);
        }
        return container;
    }

    Div buildHistoryFieldsList(AuditHistoryItemDto h, EntityRef ref) {
        AuditActivityEnrichHook enrichHook = enrichHooks.get(ref.entityType());
        Supplier<String> mediaLookup = enrichHook != null
                ? () -> enrichHook.getMediaStateAtVersion(ref, h.version())
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
            switch (entry) {
                case ChangeEntry.MediaChange _ -> mediaChanges.add(entry);
                case ChangeEntry.FieldChange _ -> textChanges.add(entry);
            }
        }

        List<ChangeEntry> expanded = expandTextFields(snapshotData, textChanges);
        for (ChangeEntry entry : expanded) {
            boolean unchanged = switch (entry) {
                case ChangeEntry.FieldChange(_, var from, _) -> from == null || from.isBlank();
                case ChangeEntry.MediaChange _               -> false;
            };
            addSpan(container, changeFormatter.format(entry), unchanged, cssBase);
        }

        if (!mediaChanges.isEmpty()) {
            mediaChanges.forEach(pc -> addSpan(container, changeFormatter.format(pc), false, cssBase));
        } else if (mediaStateLookup != null) {
            String state     = mediaStateLookup.get();
            String mediaText = (state != null && !state.isBlank()) ? state : "—";
            addSpan(container, i18n.get(AuditI18n.CHANGES_SET, i18n.get(AuditI18n.CHANGES_MEDIA), mediaText), true, cssBase);
        }

        return container;
    }

    private static List<ChangeEntry> expandTextFields(AuditableSnapshot snapshot, List<ChangeEntry> changedFields) {
        return snapshot != null ? snapshot.expandWithChanges(changedFields) : changedFields;
    }

    private void addSpan(Div container, String text, boolean unchanged, String cssBase) {
        if (text == null || text.isBlank()) return;
        Span span = new Span(i18n.get(AuditI18n.CHANGES_BULLET, text));
        span.addClassName(cssBase + "-item");
        if (unchanged) span.addClassName(cssBase + "-item--unchanged");
        container.add(span);
    }
}
