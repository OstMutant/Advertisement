package org.ost.marketplace.ui.views.components.audit;
import org.ost.marketplace.services.i18n.I18nKey;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.InstantFormatter;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.springframework.context.annotation.Scope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditTimelineRowRenderer implements Initialization<AuditTimelineRowRenderer> {

    private static final String CSS_CHANGES         = "activity-feed-changes";
    private static final String CSS_HISTORY_CHANGES = "entity-activity-changes";

    // Entity types with a real field-name-to-label mapping below -- everything else falls back
    // to changeFormatter.buildChangesList() (raw, unexpanded changes, no label translation).
    private static final Set<EntityType> LABELED_ENTITY_TYPES =
            EnumSet.of(EntityType.ADVERTISEMENT, EntityType.TAXON, EntityType.USER, EntityType.USER_SETTINGS);

    private final I18nService          i18n;
    private final InstantFormatter     formatter;
    private final AuditChangeFormatter changeFormatter;
    private final AuditQueryService    auditQueryService;

    @Override
    public AuditTimelineRowRenderer init() {
        return this;
    }

    record RowContext(
            Map<Long, String> actorNames,
            Set<EntityRef>    existingRefs) {}

    Div buildRow(@NonNull AuditTimelineItemDto<AuditableSnapshot> item, @NonNull RowContext ctx) {
        Div row = new Div();
        row.addClassName("activity-feed-row");
        if (!ctx.existingRefs().contains(item.entityRef()))
            row.addClassName("activity-feed-row--deleted");

        row.add(actionSpan(item.actionType()), typeSpan(item.entityRef().entityType()));

        String changedByName = item.changedByActorId() != null
                ? ctx.actorNames().getOrDefault(item.changedByActorId(), "") : null;
        Span editor = changeFormatter.buildEditorBadge(item.changedByActorId(), changedByName);
        Div rightGroup = editor != null ? new Div(editor, timeSpan(item.createdAt())) : new Div(timeSpan(item.createdAt()));
        rightGroup.addClassName("activity-feed-right-group");
        row.add(rightGroup);

        row.add(buildActivityFieldsList(item));
        return row;
    }

    private Span actionSpan(ActionType actionType) {
        Span span = new Span(i18n.get(I18nKey.forAction(actionType)));
        span.addClassName("activity-feed-action");
        span.addClassName("activity-feed-action--" + actionType.name().toLowerCase());
        return span;
    }

    private Span typeSpan(EntityType entityType) {
        Span span = new Span(i18n.get(I18nKey.forEntityType(entityType)));
        span.addClassName("activity-feed-type");
        span.addClassName("activity-feed-type--" + entityType.name().toLowerCase());
        return span;
    }

    private Span timeSpan(Instant createdAt) {
        Span span = new Span(formatter.formatInstantHuman(createdAt));
        span.addClassName("activity-feed-time");
        return span;
    }

    private Div buildActivityFieldsList(AuditTimelineItemDto<AuditableSnapshot> item) {
        EntityType entityType = item.entityRef().entityType();
        if (auditQueryService.hasEnrichHook(entityType)) {
            Long attachmentSnapshotId = item.snapshotData() instanceof AdvertisementSnapshotDto s
                    ? s.attachmentSnapshotId() : null;
            return buildEntityChangesDiv(item.changes(), item.snapshotData(), CSS_CHANGES,
                    attachmentSnapshotId != null
                            ? () -> auditQueryService.getMediaStateForSnapshot(item.entityRef(), attachmentSnapshotId)
                            : null,
                    entityType);
        }
        if (LABELED_ENTITY_TYPES.contains(entityType) && item.snapshotData() != null) {
            return buildActivityChangesDiv(item.expandedChanges(), entityType);
        }
        return changeFormatter.buildChangesList(item.changes(), CSS_CHANGES);
    }

    private Div buildActivityChangesDiv(List<ChangeEntry> entries, EntityType entityType) {
        Div container = new Div();
        container.addClassName(CSS_CHANGES);
        for (ChangeEntry entry : entries) {
            ChangeEntry resolved = applyLabel(entry, entityType);
            boolean unchanged = switch (resolved) {
                case ChangeEntry.FieldChange(_, var from, _) -> from == null || from.isBlank();
                case ChangeEntry.MediaChange(var before, _)  -> before == null || before.isBlank();
            };
            addEntry(container, resolved, unchanged, CSS_CHANGES);
        }
        return container;
    }

    Div buildActivityFieldsList(AuditActivityItemDto<? extends AuditableSnapshot> h, EntityRef ref) {
        Long attachmentSnapshotId = h.snapshotData() instanceof AdvertisementSnapshotDto s
                ? s.attachmentSnapshotId() : null;
        Supplier<String> mediaLookup = (attachmentSnapshotId != null && auditQueryService.hasEnrichHook(ref.entityType()))
                ? () -> auditQueryService.getMediaStateForSnapshot(ref, attachmentSnapshotId)
                : null;
        return buildEntityChangesDiv(h.changes(), h.snapshotData(), CSS_HISTORY_CHANGES, mediaLookup,
                ref.entityType());
    }

    private ChangeEntry applyLabel(ChangeEntry entry, EntityType entityType) {
        return entry.mapField(field -> labelFor(entityType, field));
    }

    // rawFieldKey is always a DTO's own @FieldNameConstants-generated Fields.* constant, never a hand-typed string.
    private String labelFor(EntityType entityType, String rawFieldKey) {
        I18nKey key = switch (entityType) {
            case ADVERTISEMENT -> switch (rawFieldKey) {
                case AdvertisementSnapshotDto.Fields.title       -> I18nKey.CHANGES_FIELD_TITLE;
                case AdvertisementSnapshotDto.Fields.description -> I18nKey.CHANGES_FIELD_DESCRIPTION;
                case AdvertisementSnapshotDto.Fields.adKind      -> I18nKey.CHANGES_FIELD_AD_KIND;
                case AdvertisementSnapshotDto.Fields.categoryIds -> I18nKey.CHANGES_FIELD_CATEGORY;
                case AdvertisementSnapshotDto.Fields.cityTaxonId -> I18nKey.CHANGES_FIELD_CITY;
                default                                          -> null;
            };
            case TAXON -> switch (rawFieldKey) {
                case TaxonSnapshotDto.Fields.nameEn        -> I18nKey.CHANGES_FIELD_NAME_EN;
                case TaxonSnapshotDto.Fields.descriptionEn -> I18nKey.CHANGES_FIELD_DESCRIPTION_EN;
                case TaxonSnapshotDto.Fields.nameUk        -> I18nKey.CHANGES_FIELD_NAME_UK;
                case TaxonSnapshotDto.Fields.descriptionUk -> I18nKey.CHANGES_FIELD_DESCRIPTION_UK;
                default                                    -> null;
            };
            case USER -> switch (rawFieldKey) {
                case UserSnapshotDto.Fields.name  -> I18nKey.CHANGES_FIELD_NAME;
                case UserSnapshotDto.Fields.email -> I18nKey.CHANGES_FIELD_EMAIL;
                case UserSnapshotDto.Fields.role  -> I18nKey.CHANGES_FIELD_ROLE;
                default                           -> null;
            };
            case USER_SETTINGS -> switch (rawFieldKey) {
                case SettingsSnapshotDto.Fields.adsPageSize      -> I18nKey.CHANGES_SETTING_ADS_PAGE_SIZE;
                case SettingsSnapshotDto.Fields.usersPageSize    -> I18nKey.CHANGES_SETTING_USERS_PAGE_SIZE;
                case SettingsSnapshotDto.Fields.timelinePageSize -> I18nKey.CHANGES_SETTING_TIMELINE_PAGE_SIZE;
                default                                          -> null;
            };
            default -> null;
        };
        return key != null ? i18n.get(key) : rawFieldKey;
    }

    private Div buildEntityChangesDiv(List<ChangeEntry> changes, AuditableSnapshot snapshotData,
                                      String cssBase, Supplier<String> mediaStateLookup,
                                      EntityType entityType) {
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
            ChangeEntry entry = applyLabel(rawEntry, entityType);
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
