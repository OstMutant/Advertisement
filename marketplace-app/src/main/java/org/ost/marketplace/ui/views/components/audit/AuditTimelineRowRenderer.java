package org.ost.marketplace.ui.views.components.audit;
import org.ost.marketplace.services.i18n.I18nKey;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.InstantFormatter;
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

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditTimelineRowRenderer implements Initialization<AuditTimelineRowRenderer> {

    private static final String CSS_CHANGES         = "activity-feed-changes";
    private static final String CSS_HISTORY_CHANGES = "entity-activity-changes";

    private final I18nService                   i18n;
    private final InstantFormatter              formatter;
    private final AuditChangeFormatter          changeFormatter;
    private final List<AuditActivityFieldsHook> fieldsProviderList;
    private final List<AuditActivityEnrichHook<?>> enrichHookList;

    private Map<EntityType, AuditActivityFieldsHook> fieldsProviders;
    private Map<EntityType, AuditActivityEnrichHook<?>> enrichHooks;

    @Override
    @PostConstruct
    public AuditTimelineRowRenderer init() {
        fieldsProviders = fieldsProviderList.stream().collect(Collectors.toMap(AuditActivityFieldsHook::entityType, h -> h, (a, _) -> a, () -> new EnumMap<>(EntityType.class)));
        enrichHooks     = enrichHookList.stream().collect(Collectors.toMap(AuditActivityEnrichHook::entityType, h -> h, (a, _) -> a, () -> new EnumMap<>(EntityType.class)));
        return this;
    }

    record RowContext(
            Map<Long, String> actorNames,
            Map<Long, String> displayNames,
            Set<EntityRef>    existingRefs) {}

    Div buildRow(@NonNull AuditTimelineItemDto<AuditableSnapshot> item, @NonNull RowContext ctx) {
        Div row = new Div();
        row.addClassName("activity-feed-row");
        if (!ctx.existingRefs().contains(item.entityRef()))
            row.addClassName("activity-feed-row--deleted");

        row.add(actionSpan(item.actionType()), typeSpan(item.entityRef().entityType().name()),
                nameSpan(ctx.displayNames().getOrDefault(item.entityRef().entityId(), "")), timeSpan(item.createdAt()));

        String changedByName = item.changedByActorId() != null
                ? ctx.actorNames().getOrDefault(item.changedByActorId(), "") : null;
        Span editor = changeFormatter.buildEditorBadge(item.changedByActorId(), changedByName);
        if (editor != null) row.add(editor);

        row.add(buildActivityFieldsList(item));
        return row;
    }

    private Span actionSpan(ActionType actionType) {
        Span span = new Span(i18n.get(I18nKey.forAction(actionType)));
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

    private Div buildActivityFieldsList(AuditTimelineItemDto<AuditableSnapshot> item) {
        AuditActivityEnrichHook<?> enrichHook = enrichHooks.get(item.entityRef().entityType());
        if (enrichHook != null) {
            Long attachmentSnapshotId = item.snapshotData() instanceof AdvertisementSnapshotDto s
                    ? s.attachmentSnapshotId() : null;
            return buildEntityChangesDiv(item.changes(), item.snapshotData(), CSS_CHANGES,
                    attachmentSnapshotId != null
                            ? () -> enrichHook.getMediaStateForSnapshot(item.entityRef(), attachmentSnapshotId)
                            : null,
                    fieldsProviders.get(item.entityRef().entityType()));
        }
        AuditActivityFieldsHook provider = fieldsProviders.get(item.entityRef().entityType());
        if (provider != null && item.snapshotData() != null) {
            return buildActivityChangesDiv(provider.expandFields(item), provider);
        }
        return changeFormatter.buildChangesList(item.changes(), CSS_CHANGES);
    }

    private Div buildActivityChangesDiv(List<ChangeEntry> entries, AuditActivityFieldsHook labelHook) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);
        for (ChangeEntry entry : entries) {
            ChangeEntry resolved = applyLabel(entry, labelHook);
            boolean unchanged = switch (resolved) {
                case ChangeEntry.FieldChange(_, var from, _) -> from == null || from.isBlank();
                case ChangeEntry.MediaChange(var before, _)  -> before == null || before.isBlank();
            };
            addEntry(container, resolved, unchanged, CSS_CHANGES);
        }
        return container;
    }

    Div buildActivityFieldsList(AuditActivityItemDto<? extends AuditableSnapshot> h, EntityRef ref) {
        AuditActivityEnrichHook<?> enrichHook = enrichHooks.get(ref.entityType());
        Long attachmentSnapshotId = h.snapshotData() instanceof AdvertisementSnapshotDto s
                ? s.attachmentSnapshotId() : null;
        Supplier<String> mediaLookup = (enrichHook != null && attachmentSnapshotId != null)
                ? () -> enrichHook.getMediaStateForSnapshot(ref, attachmentSnapshotId)
                : null;
        return buildEntityChangesDiv(h.changes(), h.snapshotData(), CSS_HISTORY_CHANGES, mediaLookup,
                fieldsProviders.get(ref.entityType()));
    }

    private static ChangeEntry applyLabel(ChangeEntry entry, AuditActivityFieldsHook labelHook) {
        if (labelHook == null) return entry;
        return switch (entry) {
            case ChangeEntry.FieldChange(var field, var from, var to) -> new ChangeEntry.FieldChange(labelHook.labelFor(field), from, to);
            case ChangeEntry.MediaChange _ -> entry;
        };
    }

    private Div buildEntityChangesDiv(List<ChangeEntry> changes, AuditableSnapshot snapshotData,
                                      String cssBase, Supplier<String> mediaStateLookup,
                                      AuditActivityFieldsHook labelHook) {
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
        for (ChangeEntry rawEntry : expanded) {
            ChangeEntry entry = applyLabel(rawEntry, labelHook);
            boolean unchanged = switch (entry) {
                case ChangeEntry.FieldChange(_, var from, _) -> from == null || from.isBlank();
                case ChangeEntry.MediaChange _               -> false;
            };
            addEntry(container, entry, unchanged, cssBase);
        }

        if (!mediaChanges.isEmpty()) {
            mediaChanges.forEach(pc -> addEntry(container, pc, false, cssBase));
        } else if (mediaStateLookup != null) {
            String state     = mediaStateLookup.get();
            String mediaText = (state != null && !state.isBlank()) ? state : "—";
            addSpan(container, i18n.get(I18nKey.AUDIT_CHANGES_SET, i18n.get(I18nKey.AUDIT_CHANGES_MEDIA), mediaText), true, cssBase);
        }

        return container;
    }

    private static List<ChangeEntry> expandTextFields(AuditableSnapshot snapshot, List<ChangeEntry> changedFields) {
        return snapshot != null ? snapshot.expandWithChanges(changedFields) : changedFields;
    }

    private void addEntry(@NonNull Div container, @NonNull ChangeEntry entry, boolean unchanged, @NonNull String cssBase) {
        changeFormatter.buildEntryInto(container, entry, cssBase, unchanged);
    }

    private void addSpan(Div container, String text, boolean unchanged, String cssBase) {
        if (text == null || text.isBlank()) return;
        Div item = new Div();
        item.addClassName(cssBase + "-item");
        if (unchanged) item.addClassName(cssBase + "-item--unchanged");
        item.getElement().setProperty("innerHTML", i18n.get(I18nKey.AUDIT_CHANGES_BULLET, text));
        container.add(item);
    }
}
