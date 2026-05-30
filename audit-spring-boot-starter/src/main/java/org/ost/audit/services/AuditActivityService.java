package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.audit.repository.AuditLogRow;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.audit.spi.ActivityEnrichHook;
import org.ost.platform.core.spi.EntityNameHook;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditActivityService {

    private final AuditLogRepository        repository;
    private final AuditJsonSerializationService jsonService;
    private final ActivityEnrichHook        activityEnrichHook;
    private final AuditDomainHelper         auditDomainHelper;
    private final List<EntityNameHook>      entityNameHooks;

    public List<AuditActivityItemDto> getForSubject(EntityType subjectType, Long subjectId) {
        List<AuditActivityItemDto> base = repository.findActivityForProfile(subjectId)
                .stream().map(this::toActivityItem).toList();

        List<AuditActivityItemDto> combined = activityEnrichHook.merge(new EntityRef(subjectType, subjectId), base);
        combined = resolveDisplayNames(combined);

        combined = auditDomainHelper.withResolvedActorNames(
                combined,
                AuditActivityItemDto::changedByActorId,
                (i, name) -> new AuditActivityItemDto(i.snapshotId(), i.entityId(), i.entityType(),
                        i.displayName(), i.actionType(), i.createdAt(), i.entityExists(),
                        i.changes(), i.changedByActorId(), name, i.snapshotData()));
        combined = resolveEntityExistence(combined);
        return combined;
    }

    private AuditActivityItemDto toActivityItem(AuditLogRow row) {
        AuditableSnapshot snapshot = jsonService.fromSnapshot(row.snapshotDataJson());
        return new AuditActivityItemDto(
                row.id(), row.entityId(), row.entityType(),
                "", row.actionType(), row.createdAt(), false,
                jsonService.fromJsonList(row.changesSummaryJson()),
                row.actorId(), null, snapshot
        );
    }

    private List<AuditActivityItemDto> resolveDisplayNames(List<AuditActivityItemDto> items) {
        return items.stream().map(i -> {
            String name = entityNameHooks.stream()
                    .filter(h -> h.supports(i.entityType()))
                    .findFirst()
                    .map(h -> h.resolveDisplayName(i.entityType(), i.snapshotData()))
                    .orElse("");
            return new AuditActivityItemDto(i.snapshotId(), i.entityId(), i.entityType(),
                    name, i.actionType(), i.createdAt(), i.entityExists(),
                    i.changes(), i.changedByActorId(), i.changedByName(), i.snapshotData());
        }).toList();
    }

    private List<AuditActivityItemDto> resolveEntityExistence(List<AuditActivityItemDto> items) {
        Map<EntityType, Set<Long>> byType = items.stream()
                .collect(Collectors.groupingBy(
                        AuditActivityItemDto::entityType,
                        Collectors.mapping(AuditActivityItemDto::entityId, Collectors.toSet())));
        Map<EntityType, Set<Long>> existingByType = byType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> auditDomainHelper.findExisting(e.getKey(), e.getValue())));
        return items.stream()
                .map(i -> {
                    boolean exists = existingByType.getOrDefault(i.entityType(), Set.of()).contains(i.entityId());
                    return new AuditActivityItemDto(i.snapshotId(), i.entityId(), i.entityType(),
                            i.displayName(), i.actionType(), i.createdAt(), exists,
                            i.changes(), i.changedByActorId(), i.changedByName(),
                            i.snapshotData());
                })
                .toList();
    }
}
