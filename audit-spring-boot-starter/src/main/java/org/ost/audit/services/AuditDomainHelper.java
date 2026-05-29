package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuditDomainHelper {

    private final ObjectProvider<AuditDomainHook> auditDomainHook;

    Map<Long, String> resolveNames(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        AuditDomainHook hook = auditDomainHook.getIfAvailable();
        return hook != null ? hook.resolveNames(ids) : Map.of();
    }

    Set<Long> findExisting(EntityType entityType, Set<Long> ids) {
        if (ids.isEmpty()) return Set.of();
        AuditDomainHook hook = auditDomainHook.getIfAvailable();
        return hook != null ? hook.findExisting(entityType, ids) : Set.of();
    }

    <T> List<T> withResolvedActorNames(List<T> items,
            Function<T, Long> actorIdGetter,
            BiFunction<T, String, T> nameApplier) {
        Set<Long> ids = items.stream()
                .map(actorIdGetter)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = resolveNames(ids);
        if (names.isEmpty()) return items;
        return items.stream()
                .map(i -> {
                    Long actorId = actorIdGetter.apply(i);
                    return actorId != null ? nameApplier.apply(i, names.getOrDefault(actorId, "—")) : i;
                })
                .toList();
    }
}
