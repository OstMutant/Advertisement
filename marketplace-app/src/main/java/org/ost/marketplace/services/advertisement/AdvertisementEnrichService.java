package org.ost.marketplace.services.advertisement;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdvertisementEnrichService {

    private final ComponentFactory<AttachmentAuditHook> attachmentAuditHookFactory;
    private final ComponentFactory<TaxonPort>           taxonPortFactory;

    public List<AuditTimelineItemDto<AdvertisementSnapshotDto>> mergeMediaChanges(
            List<AuditTimelineItemDto<AdvertisementSnapshotDto>> items) {

        Map<Long, String> nameById = resolveNames(collectTimelineCategoryIds(items));
        return items.stream().map(item -> mergeTimelineItem(item, nameById)).toList();
    }

    public List<AuditActivityItemDto<AdvertisementSnapshotDto>> enrichActivityItems(
            @NonNull List<AuditActivityItemDto<AdvertisementSnapshotDto>> items) {

        Map<Long, String> nameById = resolveNames(collectActivityCategoryIds(items));
        return items.stream().map(item -> mergeActivityItem(item, nameById)).toList();
    }

    public String getMediaStateForSnapshot(EntityRef ref, Long attachmentSnapshotId) {
        if (attachmentSnapshotId == null) return null;
        return attachmentAuditHookFactory.findIfAvailable()
                .map(h -> h.getMediaStateForSnapshot(ref, attachmentSnapshotId))
                .orElse(null);
    }

    // ── Timeline tab ─────────────────────────────────────────────────────────────────────────

    private static Set<Long> collectTimelineCategoryIds(List<AuditTimelineItemDto<AdvertisementSnapshotDto>> items) {
        Set<Long> ids = new HashSet<>();
        for (AuditTimelineItemDto<AdvertisementSnapshotDto> item : items) {
            if (item.entityRef().entityType() != EntityType.ADVERTISEMENT) continue;
            addCategoryIds(ids, item.snapshotData());
            addCategoryIds(ids, item.prevSnapshotData());
        }
        return ids;
    }

    private AuditTimelineItemDto<AdvertisementSnapshotDto> mergeTimelineItem(
            AuditTimelineItemDto<AdvertisementSnapshotDto> item, Map<Long, String> nameById) {
        if (item.entityRef().entityType() != EntityType.ADVERTISEMENT) return item;
        AdvertisementSnapshotDto snapshot = item.snapshotData();
        if (snapshot == null) return item;

        List<ChangeEntry> merged = mergeChanges(item.changes(), snapshot, item.prevSnapshotData(), nameById, false);
        return item.withChanges(merged);
    }

    // ── Activity tab ─────────────────────────────────────────────────────────────────────────

    private static Set<Long> collectActivityCategoryIds(List<AuditActivityItemDto<AdvertisementSnapshotDto>> items) {
        Set<Long> ids = new HashSet<>();
        for (AuditActivityItemDto<AdvertisementSnapshotDto> item : items) {
            addCategoryIds(ids, item.snapshotData());
            addCategoryIds(ids, item.prevSnapshotData());
        }
        return ids;
    }

    private AuditActivityItemDto<AdvertisementSnapshotDto> mergeActivityItem(
            AuditActivityItemDto<AdvertisementSnapshotDto> item, Map<Long, String> nameById) {
        AdvertisementSnapshotDto snapshot = item.snapshotData();
        if (snapshot == null) return item;

        List<ChangeEntry> merged = mergeChanges(item.changes(), snapshot, item.prevSnapshotData(), nameById, true);
        return merged == item.changes() ? item : item.withChanges(merged);
    }

    // ── Shared ───────────────────────────────────────────────────────────────────────────────

    private static final ChangeEntry NO_MEDIA_ENTRY = new ChangeEntry.MediaChange(null, "—");

    // skipMediaMergeIfUnchanged: Activity skips the media entry when attachmentSnapshotId is
    // unchanged from the previous version; Timeline always merges it (no "unchanged" concept there).
    private List<ChangeEntry> mergeChanges(List<ChangeEntry> changes, AdvertisementSnapshotDto snapshot,
                                            AdvertisementSnapshotDto prev, Map<Long, String> nameById,
                                            boolean skipMediaMergeIfUnchanged) {
        List<ChangeEntry> resolved = resolveCategories(changes, snapshot, prev, nameById);

        Long attachId     = snapshot.attachmentSnapshotId();
        Long prevAttachId = prev != null ? prev.attachmentSnapshotId() : null;
        if (skipMediaMergeIfUnchanged && attachId != null && Objects.equals(attachId, prevAttachId)) {
            return resolved;
        }
        List<ChangeEntry> mediaChanges = mediaChangesFor(attachId);
        List<ChangeEntry> merged = new ArrayList<>(mediaChanges);
        merged.addAll(resolved);
        return merged;
    }

    private List<ChangeEntry> mediaChangesFor(Long attachmentSnapshotId) {
        if (attachmentSnapshotId == null) return List.of(NO_MEDIA_ENTRY);
        return attachmentAuditHookFactory.findIfAvailable()
                .map(h -> h.getChangesBySnapshotId(attachmentSnapshotId))
                .orElse(List.of());
    }

    private static void addCategoryIds(Set<Long> ids, AdvertisementSnapshotDto snapshot) {
        if (snapshot != null) ids.addAll(snapshot.categoryIds());
    }

    // Replaces the categoryIds FieldChange's raw id strings with resolved names; every other
    // ChangeEntry (media changes, or any other field) passes through unchanged.
    private static List<ChangeEntry> resolveCategories(List<ChangeEntry> changes,
                                                        AdvertisementSnapshotDto snapshot,
                                                        AdvertisementSnapshotDto prev,
                                                        Map<Long, String> nameById) {
        if (nameById.isEmpty()) return changes;
        List<Long> currIds = snapshot.categoryIds();
        List<Long> prevIds = prev != null ? prev.categoryIds() : List.of();
        return changes.stream()
                .map(entry -> entry.replaceIfField(AdvertisementSnapshotDto.Fields.categoryIds,
                        _ -> idsToNames(prevIds, nameById),
                        _ -> idsToNames(currIds, nameById)))
                .toList();
    }

    private static String idsToNames(List<Long> ids, Map<Long, String> nameById) {
        return ids.stream()
                .map(id -> nameById.getOrDefault(id, String.valueOf(id)))
                .collect(Collectors.joining(", "));
    }

    private Map<Long, String> resolveNames(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.findByIds(ids, Locale.ENGLISH))
                .map(m -> m.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> nameOrStrikethrough(e.getValue()))))
                .orElse(Map.of());
    }

    private static String nameOrStrikethrough(TaxonDto taxon) {
        return taxon.isDeleted() ? "<s>" + taxon.getName() + "</s>" : taxon.getName();
    }
}
