package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotPayloadDto;
import org.ost.audit.model.AuditSnapshotMapper;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
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

    private final AuditLogRepository                        auditLogRepository;
    private final AuditSnapshotMapper                       mapper;
    private final ObjectProvider<AttachmentAuditHook>       attachmentAuditHook;
    private final AuditDomainHelper                         auditDomainHelper;

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long currentUserId, boolean showAll) {
        List<EntityHistoryDto> history = auditLogRepository.getEntityHistory(
                entityType, entityId, showAll ? null : currentUserId);

        history = resolveActorNames(history);

        AttachmentAuditHook ext = attachmentAuditHook.getIfAvailable();
        if (ext == null) return history;
        return history.stream()
                .map(h -> {
                    List<ChangeEntry> mediaChanges = ext.getMediaChanges(new EntityRef(entityType, entityId), h.version());
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

    public Optional<SnapshotPayloadDto> getLastSnapshotPayload(EntityType entityType, Long entityId) {
        return auditLogRepository.getLastSnapshotData(entityType, entityId)
                .map(SnapshotPayloadDto::new);
    }

    private List<EntityHistoryDto> resolveActorNames(List<EntityHistoryDto> items) {
        Set<Long> ids = items.stream()
                .map(EntityHistoryDto::actorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = auditDomainHelper.resolveNames(ids);
        if (names.isEmpty()) return items;
        return items.stream()
                .map(h -> h.actorId() == null ? h
                        : new EntityHistoryDto(h.snapshotId(), h.version(), h.actionType(),
                                h.actorId(), names.getOrDefault(h.actorId(), "—"),
                                h.createdAt(),
                                h.changes(), h.prevSnapshotId(), h.snapshotData(), h.prevSnapshotData()))
                .toList();
    }
}
