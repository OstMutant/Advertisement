package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotPayload;
import org.ost.audit.model.AuditSnapshotMapper;
import org.ost.audit.repository.AuditReadRepository;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.AdvertisementHistoryExtension;
import org.ost.platform.core.spi.AuditActorNameResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AuditHistoryService {

    private final AuditReadRepository                            auditReadRepository;
    private final AuditSnapshotMapper                           mapper;
    private final ObjectProvider<AdvertisementHistoryExtension> historyExtension;
    private final ObjectProvider<AuditActorNameResolver>        actorNameResolver;

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long currentUserId, boolean showAll) {
        List<EntityHistoryDto> history = auditReadRepository.getEntityHistory(
                entityType, entityId, showAll ? null : currentUserId);

        history = resolveActorNames(history);

        AdvertisementHistoryExtension ext = historyExtension.getIfAvailable();
        if (ext == null) return history;
        return history.stream()
                .map(h -> {
                    List<ChangeEntry> mediaChanges = ext.getMediaChanges(entityType, entityId, h.version());
                    if (mediaChanges.isEmpty()) return h;
                    List<ChangeEntry> combined = new ArrayList<>(mediaChanges);
                    combined.addAll(h.changes());
                    return new EntityHistoryDto(h.snapshotId(), h.version(), h.actionType(),
                            h.actorId(), h.changedByUserName(), h.createdAt(),
                            combined, h.prevSnapshotId(), h.snapshotData(), h.prevSnapshotData());
                })
                .toList();
    }

    @Transactional
    public void appendNoteToLastSnapshot(EntityType entityType, Long entityId, String note) {
        Long snapshotId = auditReadRepository.findLastSnapshotId(entityType, entityId).orElse(null);
        if (snapshotId == null) return;
        String currentJson = auditReadRepository.getChangesSummary(snapshotId);
        List<ChangeEntry> entries = new ArrayList<>(mapper.fromJsonList(currentJson));
        entries.add(new ChangeEntry.NoteEntry(note));
        auditReadRepository.updateChangesSummary(snapshotId, mapper.toJson(entries));
    }

    public Optional<SnapshotPayload> getLastSnapshotPayload(EntityType entityType, Long entityId) {
        return auditReadRepository.getLastSnapshotData(entityType, entityId)
                .map(SnapshotPayload::new);
    }

    private List<EntityHistoryDto> resolveActorNames(List<EntityHistoryDto> items) {
        AuditActorNameResolver resolver = actorNameResolver.getIfAvailable();
        if (resolver == null) return items;
        Set<Long> ids = items.stream()
                .map(EntityHistoryDto::actorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return items;
        Map<Long, String> names = resolver.resolveNames(ids);
        return items.stream()
                .map(h -> h.actorId() == null ? h
                        : new EntityHistoryDto(h.snapshotId(), h.version(), h.actionType(),
                                h.actorId(), names.getOrDefault(h.actorId(), "—"),
                                h.createdAt(),
                                h.changes(), h.prevSnapshotId(), h.snapshotData(), h.prevSnapshotData()))
                .toList();
    }
}
