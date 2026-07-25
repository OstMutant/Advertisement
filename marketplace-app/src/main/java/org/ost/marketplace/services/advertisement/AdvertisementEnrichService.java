package org.ost.marketplace.services.advertisement;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.marketplace.services.i18n.LocaleProvider;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdvertisementEnrichService {

    private final ComponentFactory<AttachmentAuditHook> attachmentAuditHookFactory;
    private final ComponentFactory<TaxonPort>           taxonPortFactory;
    private final LocaleProvider                        localeProvider;

    public List<AuditTimelineItemDto<AdvertisementSnapshotDto>> mergeMediaChanges(
            List<AuditTimelineItemDto<AdvertisementSnapshotDto>> items) {

        Map<Long, String> nameById = resolveNames(collectTimelineTaxonIds(items));
        return items.stream().map(item -> mergeTimelineItem(item, nameById)).toList();
    }

    public List<AuditActivityItemDto<AdvertisementSnapshotDto>> enrichActivityItems(
            @NonNull List<AuditActivityItemDto<AdvertisementSnapshotDto>> items) {

        Map<Long, String> nameById = resolveNames(collectActivityTaxonIds(items));
        return items.stream().map(item -> mergeActivityItem(item, nameById)).toList();
    }

    public String getMediaStateForSnapshot(EntityRef ref, Long attachmentSnapshotId) {
        if (attachmentSnapshotId == null) return null;
        return attachmentAuditHookFactory.findIfAvailable()
                .map(h -> h.getMediaStateForSnapshot(ref, attachmentSnapshotId))
                .orElse(null);
    }

    // ── Timeline tab ─────────────────────────────────────────────────────────────────────────

    private static Set<Long> collectTimelineTaxonIds(List<AuditTimelineItemDto<AdvertisementSnapshotDto>> items) {
        Set<Long> ids = new HashSet<>();
        for (AuditTimelineItemDto<AdvertisementSnapshotDto> item : items) {
            if (item.entityRef().entityType() != EntityType.ADVERTISEMENT) continue;
            addTaxonIds(ids, item.snapshotData());
            addTaxonIds(ids, item.prevSnapshotData());
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

    private static Set<Long> collectActivityTaxonIds(List<AuditActivityItemDto<AdvertisementSnapshotDto>> items) {
        Set<Long> ids = new HashSet<>();
        for (AuditActivityItemDto<AdvertisementSnapshotDto> item : items) {
            addTaxonIds(ids, item.snapshotData());
            addTaxonIds(ids, item.prevSnapshotData());
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

    // skipMediaMergeIfUnchanged: Activity skips an unchanged media entry; Timeline always merges it.
    private List<ChangeEntry> mergeChanges(List<ChangeEntry> changes, AdvertisementSnapshotDto snapshot,
                                            AdvertisementSnapshotDto prev, Map<Long, String> nameById,
                                            boolean skipMediaMergeIfUnchanged) {
        List<ChangeEntry> resolved = resolveCity(resolveCategories(changes, snapshot, prev, nameById), snapshot, prev, nameById);

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

    private static void addTaxonIds(Set<Long> ids, AdvertisementSnapshotDto snapshot) {
        if (snapshot == null) return;
        ids.addAll(snapshot.categoryIds());
        if (snapshot.cityTaxonId() != null) ids.add(snapshot.cityTaxonId());
    }

    // Appends a resolved categoryIds entry when categories exist -- expandWithChanges() falls back to allFields()'s raw ids otherwise.
    private static List<ChangeEntry> resolveCategories(List<ChangeEntry> changes,
                                                        AdvertisementSnapshotDto snapshot,
                                                        AdvertisementSnapshotDto prev,
                                                        Map<Long, String> nameById) {
        if (nameById.isEmpty()) return changes;
        List<Long> currIds = snapshot.categoryIds();
        List<Long> prevIds = prev != null ? prev.categoryIds() : List.of();
        List<ChangeEntry> resolved = changes.stream()
                .map(entry -> entry.replaceIfField(AdvertisementSnapshotDto.Fields.categoryIds,
                        _ -> idsToNames(prevIds, nameById),
                        _ -> idsToNames(currIds, nameById)))
                .toList();
        boolean hasCategoryEntry = resolved.stream().anyMatch(
                e -> e instanceof ChangeEntry.FieldChange(var field, var _, var _)
                        && field.equals(AdvertisementSnapshotDto.Fields.categoryIds));
        if (hasCategoryEntry || (currIds.isEmpty() && prevIds.isEmpty())) return resolved;
        List<ChangeEntry> withCategory = new ArrayList<>(resolved);
        withCategory.add(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, null, idsToNames(currIds, nameById)));
        return withCategory;
    }

    private static String idsToNames(List<Long> ids, Map<Long, String> nameById) {
        return ids.stream()
                .map(id -> nameById.getOrDefault(id, String.valueOf(id)))
                .collect(Collectors.joining(", "));
    }

    // Same shape as resolveCategories() above, for the single-valued cityTaxonId field.
    private static List<ChangeEntry> resolveCity(List<ChangeEntry> changes,
                                                  AdvertisementSnapshotDto snapshot,
                                                  AdvertisementSnapshotDto prev,
                                                  Map<Long, String> nameById) {
        if (nameById.isEmpty()) return changes;
        Long currId = snapshot.cityTaxonId();
        Long prevId = prev != null ? prev.cityTaxonId() : null;
        List<ChangeEntry> resolved = changes.stream()
                .map(entry -> entry.replaceIfField(AdvertisementSnapshotDto.Fields.cityTaxonId,
                        _ -> idToName(prevId, nameById),
                        _ -> idToName(currId, nameById)))
                .toList();
        boolean hasCityEntry = resolved.stream().anyMatch(
                e -> e instanceof ChangeEntry.FieldChange(var field, var _, var _)
                        && field.equals(AdvertisementSnapshotDto.Fields.cityTaxonId));
        if (hasCityEntry || (currId == null && prevId == null)) return resolved;
        List<ChangeEntry> withCity = new ArrayList<>(resolved);
        withCity.add(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.cityTaxonId, null, idToName(currId, nameById)));
        return withCity;
    }

    private static String idToName(Long id, Map<Long, String> nameById) {
        return id == null ? "" : nameById.getOrDefault(id, String.valueOf(id));
    }

    private Map<Long, String> resolveNames(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.findByIds(ids, localeProvider.getCurrentLocale()))
                .map(m -> m.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> nameOrStrikethrough(e.getValue()))))
                .orElse(Map.of());
    }

    private static String nameOrStrikethrough(TaxonDto taxon) {
        return taxon.isDeleted() ? "<s>" + taxon.getName() + "</s>" : taxon.getName();
    }
}
