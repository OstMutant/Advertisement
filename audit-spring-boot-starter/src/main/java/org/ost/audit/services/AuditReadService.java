package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.audit.repository.AuditLogProjection;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.audit.spi.ActivityEnrichHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.EntityNameHook;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AuditReadService {

    private final AuditLogRepository   repository;
    private final ActivityEnrichHook   activityEnrichHook;
    private final AuditDomainHelper    auditDomainHelper;
    private final List<EntityNameHook> entityNameHooks;

    // ── History ───────────────────────────────────────────────────────────────

    public List<AuditHistoryItemDto> getEntityHistory(EntityType entityType, Long entityId,
                                                      Long currentUserId, boolean showAll) {
        List<AuditHistoryItemDto> history = repository
                .findRows(entityType, entityId, showAll ? null : currentUserId)
                .stream().limit(100).map(this::toHistoryItem).toList();

        history = auditDomainHelper.withResolvedActorNames(
                history,
                AuditHistoryItemDto::actorId,
                (h, name) -> h.withChangedByUserName(name));

        return history.stream()
                .map(h -> {
                    List<ChangeEntry> mediaChanges = activityEnrichHook.getAdditionalChanges(
                            new EntityRef(entityType, entityId), h.version());
                    if (mediaChanges.isEmpty()) return h;
                    List<ChangeEntry> combined = new ArrayList<>(mediaChanges);
                    combined.addAll(h.changes());
                    return h.withChanges(combined);
                })
                .toList();
    }

    public Optional<AuditableSnapshot> getLastSnapshot(EntityType entityType, Long entityId) {
        return repository.getLastSnapshot(entityType, entityId);
    }

    // ── Activity ──────────────────────────────────────────────────────────────

    public List<AuditActivityItemDto> getForSubject(List<EntityRef> subjects, Long actorId) {
        Stream<AuditLogProjection> subjectRows = subjects.stream()
                .flatMap(s -> repository.findRows(s.entityType(), s.entityId(), null).stream());
        Stream<AuditLogProjection> actorRows = actorId != null
                ? repository.findRowsByActor(actorId).stream()
                : Stream.empty();

        List<AuditActivityItemDto> base = Stream.concat(subjectRows, actorRows)
                .collect(Collectors.toMap(AuditLogProjection::id, r -> r, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparing(AuditLogProjection::createdAt).reversed())
                .limit(20)
                .map(this::toActivityItem)
                .toList();

        List<AuditActivityItemDto> combined = activityEnrichHook.merge(subjects, base);
        combined = resolveDisplayNames(combined);
        combined = auditDomainHelper.withResolvedActorNames(
                combined,
                AuditActivityItemDto::changedByActorId,
                (i, name) -> i.withChangedByName(name));
        combined = resolveEntityExistence(combined);
        return combined;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AuditHistoryItemDto toHistoryItem(AuditLogProjection row) {
        return new AuditHistoryItemDto(
                row.id(), row.version(), row.actionType(), row.actorId(), null, row.createdAt(),
                row.snapshot().diff(row.prevSnapshot()), row.prevId(), row.snapshot(), row.prevSnapshot());
    }

    private AuditActivityItemDto toActivityItem(AuditLogProjection row) {
        return new AuditActivityItemDto(
                row.id(), row.entityId(), row.entityType(),
                "", row.actionType(), row.createdAt(), false,
                row.snapshot().diff(row.prevSnapshot()), row.actorId(), null, row.snapshot());
    }

    // ── Post-processing ───────────────────────────────────────────────────────

    private List<AuditActivityItemDto> resolveDisplayNames(List<AuditActivityItemDto> items) {
        return items.stream().map(i -> {
            String name = entityNameHooks.stream()
                    .filter(h -> h.supports(i.entityType()))
                    .findFirst()
                    .map(h -> h.resolveDisplayName(i.entityType(), i.snapshotData()))
                    .orElse("");
            return i.withDisplayName(name);
        }).toList();
    }

    private List<AuditActivityItemDto> resolveEntityExistence(List<AuditActivityItemDto> items) {
        Map<EntityType, Set<Long>> byType = items.stream()
                .collect(Collectors.groupingBy(
                        AuditActivityItemDto::entityType,
                        Collectors.mapping(AuditActivityItemDto::entityId, Collectors.toSet())));
        Map<EntityType, Set<Long>> existingByType = byType.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> auditDomainHelper.findExisting(e.getKey(), e.getValue())));
        return items.stream()
                .map(i -> {
                    boolean exists = existingByType.getOrDefault(i.entityType(), Set.of()).contains(i.entityId());
                    return i.withEntityExists(exists);
                })
                .toList();
    }
}
