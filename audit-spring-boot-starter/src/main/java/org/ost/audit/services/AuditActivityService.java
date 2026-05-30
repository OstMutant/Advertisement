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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AuditActivityService {

    private final AuditLogRepository            repository;
    private final AuditJsonSerializationService jsonService;
    private final AuditDiffService              diffService;
    private final ActivityEnrichHook            activityEnrichHook;
    private final AuditDomainHelper             auditDomainHelper;
    private final List<EntityNameHook>          entityNameHooks;

    public List<AuditActivityItemDto> getForSubject(List<EntityRef> subjects, Long actorId) {
        Stream<AuditLogRow> subjectRows = subjects.stream()
                .flatMap(s -> repository.findRows(s.entityType(), s.entityId(), null).stream());
        Stream<AuditLogRow> actorRows = actorId != null
                ? repository.findRowsByActor(actorId).stream()
                : Stream.empty();

        List<AuditActivityItemDto> base = Stream.concat(subjectRows, actorRows)
                .collect(Collectors.toMap(AuditLogRow::id, r -> r, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparing(AuditLogRow::createdAt).reversed())
                .limit(20)
                .map(this::toActivityItem)
                .toList();

        List<AuditActivityItemDto> combined = activityEnrichHook.merge(subjects, base);
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
        AuditableSnapshot current  = jsonService.fromSnapshot(row.snapshotDataJson());
        AuditableSnapshot previous = jsonService.fromSnapshot(row.prevSnapshotDataJson());
        return new AuditActivityItemDto(
                row.id(), row.entityId(), row.entityType(),
                "", row.actionType(), row.createdAt(), false,
                diffService.diff(previous, current),
                row.actorId(), null, current
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
