package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.audit.services.AuditJsonSerializationService;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.audit.spi.ActivityEnrichHook;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditHistoryService {

    private final AuditLogRepository            auditLogRepository;
    private final AuditJsonSerializationService mapper;
    private final ActivityEnrichHook            activityEnrichHook;
    private final AuditDomainHelper             auditDomainHelper;

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long currentUserId, boolean showAll) {
        List<EntityHistoryDto> history = auditLogRepository.getEntityHistory(
                entityType, entityId, showAll ? null : currentUserId);

        history = auditDomainHelper.withResolvedActorNames(
                history,
                EntityHistoryDto::actorId,
                (h, name) -> new EntityHistoryDto(h.snapshotId(), h.version(), h.actionType(),
                        h.actorId(), name, h.createdAt(),
                        h.changes(), h.prevSnapshotId(), h.snapshotData(), h.prevSnapshotData()));

        return history.stream()
                .map(h -> {
                    List<ChangeEntry> mediaChanges = activityEnrichHook.getAdditionalChanges(new EntityRef(entityType, entityId), h.version());
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
        Long snapshotId = auditLogRepository.findLastSnapshotId(entityType, entityId).orElse(null);
        if (snapshotId == null) return;
        String currentJson = auditLogRepository.getChangesSummary(snapshotId);
        List<ChangeEntry> entries = new ArrayList<>(mapper.fromJsonList(currentJson));
        entries.add(new ChangeEntry.NoteEntry(note));
        auditLogRepository.updateChangesSummary(snapshotId, mapper.toJson(entries));
    }

    public Optional<AuditableSnapshot> getLastSnapshot(EntityType entityType, Long entityId) {
        return auditLogRepository.getLastSnapshotData(entityType, entityId)
                .map(mapper::fromSnapshot);
    }

}
