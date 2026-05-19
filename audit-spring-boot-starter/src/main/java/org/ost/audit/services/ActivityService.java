package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.audit.repository.ActivityRepository;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.audit.spi.ActivityFeedExtension;
import org.ost.platform.audit.spi.AuditActorNameResolver;
import org.ost.platform.audit.spi.AuditEntityExistenceChecker;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository                          repository;
    private final ObjectProvider<ActivityFeedExtension>       activityExtension;
    private final ObjectProvider<AuditActorNameResolver>      actorNameResolver;
    private final ObjectProvider<AuditEntityExistenceChecker> existenceChecker;

    public List<ActivityItemDto> getForSubject(EntityType subjectType, Long subjectId) {
        List<ActivityItemDto> base = repository.findByActorId(subjectId);

        ActivityFeedExtension ext = activityExtension.getIfAvailable();
        List<ActivityItemDto> combined = ext != null ? ext.merge(subjectType, subjectId, base) : base;

        combined = resolveActorNames(combined);
        combined = resolveEntityExistence(combined);
        return combined;
    }

    private List<ActivityItemDto> resolveActorNames(List<ActivityItemDto> items) {
        AuditActorNameResolver resolver = actorNameResolver.getIfAvailable();
        if (resolver == null) return items;
        Set<Long> ids = items.stream()
                .map(ActivityItemDto::changedByActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return items;
        Map<Long, String> names = resolver.resolveNames(ids);
        return items.stream()
                .map(i -> i.changedByActorId() == null ? i
                        : new ActivityItemDto(i.snapshotId(), i.entityId(), i.entityType(),
                                i.displayName(), i.actionType(), i.createdAt(), i.entityExists(),
                                i.changes(), i.changedByActorId(),
                                names.getOrDefault(i.changedByActorId(), "—"),
                                i.snapshotData()))
                .toList();
    }

    private List<ActivityItemDto> resolveEntityExistence(List<ActivityItemDto> items) {
        AuditEntityExistenceChecker checker = existenceChecker.getIfAvailable();
        if (checker == null) return items;
        Map<EntityType, Set<Long>> byType = items.stream()
                .collect(Collectors.groupingBy(
                        ActivityItemDto::entityType,
                        Collectors.mapping(ActivityItemDto::entityId, Collectors.toSet())));
        Map<EntityType, Set<Long>> existingByType = byType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> checker.findExisting(e.getKey(), e.getValue())));
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
