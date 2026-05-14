package org.ost.advertisement.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.model.AuditSnapshotMapper;
import org.ost.advertisement.audit.repository.AuditReadRepository;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.ost.advertisement.events.spi.AuditActorNameResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AuditHistoryService {

    private final AuditReadRepository                            auditReadRepository;
    private final AuditSnapshotMapper                           mapper;
    private final ObjectProvider<AdvertisementHistoryExtension> historyExtension;
    private final ObjectProvider<AuditActorNameResolver>        actorNameResolver;

    public List<AdvertisementHistoryDto> getAdvertisementHistory(Long advertisementId, Long currentUserId, boolean showAll) {
        List<AdvertisementHistoryDto> history = auditReadRepository.getAdvertisementHistory(
                advertisementId, showAll ? null : currentUserId);

        history = resolveActorNames(history);

        AdvertisementHistoryExtension ext = historyExtension.getIfAvailable();
        if (ext == null) return history;
        return history.stream()
                .map(h -> {
                    List<ChangeEntry> mediaChanges = ext.getMediaChanges(advertisementId, h.version());
                    if (mediaChanges.isEmpty()) return h;
                    List<ChangeEntry> combined = new ArrayList<>(mediaChanges);
                    combined.addAll(h.changes());
                    return new AdvertisementHistoryDto(h.snapshotId(), h.version(), h.actionType(),
                            h.actorId(), h.changedByUserName(), h.createdAt(), h.title(), h.description(),
                            combined, h.prevSnapshotId(), h.prevTitle(), h.prevDescription());
                })
                .toList();
    }

    @Transactional
    public void appendNoteToLastSnapshot(Long advertisementId, String note) {
        Long snapshotId = auditReadRepository.findLastSnapshotId(advertisementId).orElse(null);
        if (snapshotId == null) return;
        String currentJson = auditReadRepository.getChangesSummary(snapshotId);
        List<ChangeEntry> entries = new ArrayList<>(mapper.fromJsonList(currentJson));
        entries.add(new ChangeEntry.NoteEntry(note));
        auditReadRepository.updateChangesSummary(snapshotId, mapper.toJson(entries));
    }

    private List<AdvertisementHistoryDto> resolveActorNames(List<AdvertisementHistoryDto> items) {
        AuditActorNameResolver resolver = actorNameResolver.getIfAvailable();
        if (resolver == null) return items;
        Set<Long> ids = items.stream()
                .map(AdvertisementHistoryDto::actorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return items;
        Map<Long, String> names = resolver.resolveNames(ids);
        return items.stream()
                .map(h -> h.actorId() == null ? h
                        : new AdvertisementHistoryDto(h.snapshotId(), h.version(), h.actionType(),
                                h.actorId(), names.getOrDefault(h.actorId(), "—"),
                                h.createdAt(), h.title(), h.description(),
                                h.changes(), h.prevSnapshotId(), h.prevTitle(), h.prevDescription()))
                .toList();
    }
}
