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
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
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
            @SuppressWarnings("unused") List<EntityRef> subjects,
            List<AuditTimelineItemDto<AdvertisementSnapshotDto>> items) {
        return items.stream()
                .map(item -> {
                    if (item.entityRef().entityType() != EntityType.ADVERTISEMENT) return item;
                    AdvertisementSnapshotDto snapshot = item.snapshotData();
                    if (snapshot == null) return item;
                    Long attachmentSnapshotId = snapshot.attachmentSnapshotId();
                    if (attachmentSnapshotId == null) return item;
                    List<ChangeEntry> mediaChanges = attachmentAuditHookFactory.findIfAvailable()
                            .map(h -> h.getChangesBySnapshotId(attachmentSnapshotId))
                            .orElse(List.of());
                    if (mediaChanges.isEmpty()) return item;
                    List<ChangeEntry> merged = new ArrayList<>(mediaChanges);
                    merged.addAll(item.changes());
                    return item.withChanges(merged);
                })
                .toList();
    }

    public List<AuditActivityItemDto<AdvertisementSnapshotDto>> enrichActivityItems(
            @SuppressWarnings("unused") @NonNull EntityRef entityRef,
            @NonNull List<AuditActivityItemDto<AdvertisementSnapshotDto>> items) {

        Map<Long, String> nameById = resolveCategoryNames(items);

        return items.stream().map(item -> {
            AdvertisementSnapshotDto snapshot = item.snapshotData();
            if (snapshot == null) return item;

            AdvertisementSnapshotDto prev     = item.prevSnapshotData();
            Long attachId                     = snapshot.attachmentSnapshotId();
            Long prevAttachId                 = prev != null ? prev.attachmentSnapshotId() : null;

            List<ChangeEntry> resolved = resolveCategories(item.changes(), snapshot, prev, nameById);

            if (attachId != null && !Objects.equals(attachId, prevAttachId)) {
                List<ChangeEntry> mediaChanges = attachmentAuditHookFactory.findIfAvailable()
                        .map(h -> h.getChangesBySnapshotId(attachId))
                        .orElse(List.of());
                if (!mediaChanges.isEmpty()) {
                    List<ChangeEntry> merged = new ArrayList<>(mediaChanges);
                    merged.addAll(resolved);
                    return item.withChanges(merged);
                }
            }

            return resolved == item.changes() ? item : item.withChanges(resolved);
        }).toList();
    }

    public String getMediaStateForSnapshot(EntityRef ref, Long attachmentSnapshotId) {
        if (attachmentSnapshotId == null) return null;
        return attachmentAuditHookFactory.findIfAvailable()
                .map(h -> h.getMediaStateForSnapshot(ref, attachmentSnapshotId))
                .orElse(null);
    }

    private Map<Long, String> resolveCategoryNames(List<AuditActivityItemDto<AdvertisementSnapshotDto>> items) {
        Set<Long> ids = new HashSet<>();
        for (AuditActivityItemDto<AdvertisementSnapshotDto> item : items) {
            if (item.snapshotData() != null)     ids.addAll(item.snapshotData().categoryIds());
            if (item.prevSnapshotData() != null) ids.addAll(item.prevSnapshotData().categoryIds());
        }
        if (ids.isEmpty()) return Map.of();
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.listAllByType(TaxonType.CATEGORY, Locale.ENGLISH, true).stream()
                        .filter(t -> ids.contains(t.getId()))
                        .collect(Collectors.toMap(TaxonDto::getId, TaxonDto::getName)))
                .orElse(Map.of());
    }

    private static List<ChangeEntry> resolveCategories(List<ChangeEntry> changes,
                                                        AdvertisementSnapshotDto snapshot,
                                                        AdvertisementSnapshotDto prev,
                                                        Map<Long, String> nameById) {
        if (nameById.isEmpty()) return changes;
        List<Long> currIds = snapshot.categoryIds();
        List<Long> prevIds = prev != null ? prev.categoryIds() : List.of();
        return changes.stream().map(entry -> switch (entry) {
            case ChangeEntry.FieldChange fc when AdvertisementSnapshotDto.Fields.categoryIds.equals(fc.field()) ->
                    new ChangeEntry.FieldChange(fc.field(), idsToNames(prevIds, nameById), idsToNames(currIds, nameById));
            default -> entry;
        }).toList();
    }

    private static String idsToNames(List<Long> ids, Map<Long, String> nameById) {
        return ids.stream()
                .map(id -> nameById.getOrDefault(id, String.valueOf(id)))
                .collect(Collectors.joining(", "));
    }
}
