package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.audit.spi.ActivityEnrichHook;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final AuditLogRepository     repository;
    private final ActivityEnrichHook     activityEnrichHook;
    private final AuditDomainHelper      auditDomainHelper;

    public List<ActivityItemDto> getForSubject(EntityType subjectType, Long subjectId) {
        List<ActivityItemDto> base = repository.findActivityForProfile(subjectId);

        List<ActivityItemDto> combined = activityEnrichHook.merge(new EntityRef(subjectType, subjectId), base);

        combined = auditDomainHelper.withResolvedActorNames(
                combined,
                ActivityItemDto::changedByActorId,
                (i, name) -> new ActivityItemDto(i.snapshotId(), i.entityId(), i.entityType(),
                        i.displayName(), i.actionType(), i.createdAt(), i.entityExists(),
                        i.changes(), i.changedByActorId(), name, i.snapshotData()));
        combined = resolveEntityExistence(combined);
        return combined;
    }

    private List<ActivityItemDto> resolveEntityExistence(List<ActivityItemDto> items) {
        Map<EntityType, Set<Long>> byType = items.stream()
                .collect(Collectors.groupingBy(
                        ActivityItemDto::entityType,
                        Collectors.mapping(ActivityItemDto::entityId, Collectors.toSet())));
        Map<EntityType, Set<Long>> existingByType = byType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> auditDomainHelper.findExisting(e.getKey(), e.getValue())));
        return items.stream()
                .map(i -> {
                    boolean exists = existingByType.getOrDefault(i.entityType(), Set.of()).contains(i.entityId());
                    return new ActivityItemDto(i.snapshotId(), i.entityId(), i.entityType(),
                            i.displayName(), i.actionType(), i.createdAt(), exists,
                            i.changes(), i.changedByActorId(), i.changedByName(),
                            i.snapshotData());
                })
                .toList();
    }
}
