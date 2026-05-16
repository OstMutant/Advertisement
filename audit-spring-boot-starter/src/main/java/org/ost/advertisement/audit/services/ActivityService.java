package org.ost.advertisement.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.repository.ActivityRepository;
import org.ost.advertisement.audit.dto.ActivityItemDto;
import org.ost.advertisement.core.model.EntityType;
import org.ost.advertisement.core.spi.AuditActorNameResolver;
import org.ost.advertisement.core.spi.AuditEntityExistenceChecker;
import org.ost.advertisement.core.spi.UserActivityExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository                          repository;
    private final ObjectProvider<UserActivityExtension>       activityExtension;
    private final ObjectProvider<AuditActorNameResolver>      actorNameResolver;
    private final ObjectProvider<AuditEntityExistenceChecker> existenceChecker;

    public List<ActivityItemDto> getForUser(Long userId) {
        List<ActivityItemDto> base = repository.findByUserId(userId);

        UserActivityExtension ext = activityExtension.getIfAvailable();
        List<ActivityItemDto> combined = new ArrayList<>(base);
        if (ext != null) {
            combined.addAll(ext.getMediaActivity(userId));
            combined.sort(Comparator.comparing(ActivityItemDto::createdAt).reversed());
            if (combined.size() > 20) combined = combined.subList(0, 20);
        }

        combined = resolveActorNames(combined);
        combined = resolveEntityExistence(combined);
        return combined;
    }

    private List<ActivityItemDto> resolveActorNames(List<ActivityItemDto> items) {
        AuditActorNameResolver resolver = actorNameResolver.getIfAvailable();
        if (resolver == null) return items;
        Set<Long> ids = items.stream()
                .map(ActivityItemDto::changedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return items;
        Map<Long, String> names = resolver.resolveNames(ids);
        return items.stream()
                .map(i -> i.changedByUserId() == null ? i
                        : new ActivityItemDto(i.snapshotId(), i.entityId(), i.entityType(),
                                i.displayName(), i.actionType(), i.createdAt(), i.entityExists(),
                                i.changes(), i.changedByUserId(),
                                names.getOrDefault(i.changedByUserId(), "—"),
                                i.snapshotTitle(), i.snapshotDescription(),
                                i.snapshotEmail(), i.snapshotRole()))
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
                            i.changes(), i.changedByUserId(), i.changedByName(),
                            i.snapshotTitle(), i.snapshotDescription(),
                            i.snapshotEmail(), i.snapshotRole());
                })
                .toList();
    }
}
