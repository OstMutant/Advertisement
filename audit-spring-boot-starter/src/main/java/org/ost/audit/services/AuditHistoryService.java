package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.audit.repository.AuditLogRow;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.audit.spi.ActivityEnrichHook;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditHistoryService {

    private final AuditLogRepository        auditLogRepository;
    private final AuditJsonSerializationService jsonService;
    private final AuditDiffService          diffService;
    private final ActivityEnrichHook        activityEnrichHook;
    private final AuditDomainHelper         auditDomainHelper;

    public List<AuditHistoryItemDto> getEntityHistory(EntityType entityType, Long entityId, Long currentUserId, boolean showAll) {
        List<AuditHistoryItemDto> history = auditLogRepository
                .findRows(entityType, entityId, showAll ? null : currentUserId)
                .stream().limit(100).map(this::toHistoryItem).toList();

        history = auditDomainHelper.withResolvedActorNames(
                history,
                AuditHistoryItemDto::actorId,
                (h, name) -> new AuditHistoryItemDto(h.snapshotId(), h.version(), h.actionType(),
                        h.actorId(), name, h.createdAt(),
                        h.changes(), h.prevSnapshotId(), h.snapshotData(), h.prevSnapshotData()));

        return history.stream()
                .map(h -> {
                    List<ChangeEntry> mediaChanges = activityEnrichHook.getAdditionalChanges(new EntityRef(entityType, entityId), h.version());
                    if (mediaChanges.isEmpty()) return h;
                    List<ChangeEntry> combined = new ArrayList<>(mediaChanges);
                    combined.addAll(h.changes());
                    return new AuditHistoryItemDto(h.snapshotId(), h.version(), h.actionType(),
                            h.actorId(), h.changedByUserName(), h.createdAt(),
                            combined, h.prevSnapshotId(), h.snapshotData(), h.prevSnapshotData());
                })
                .toList();
    }

    public Optional<AuditableSnapshot> getLastSnapshot(EntityType entityType, Long entityId) {
        return auditLogRepository.getLastSnapshot(entityType, entityId);
    }

    private AuditHistoryItemDto toHistoryItem(AuditLogRow row) {
        AuditableSnapshot current  = jsonService.fromSnapshot(row.snapshotDataJson());
        AuditableSnapshot previous = jsonService.fromSnapshot(row.prevSnapshotDataJson());
        return new AuditHistoryItemDto(
                row.id(),
                row.version(),
                row.actionType(),
                row.actorId(),
                null,
                row.createdAt(),
                diffService.diff(previous, current),
                row.prevId(),
                current,
                previous
        );
    }
}
