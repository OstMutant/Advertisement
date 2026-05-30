package org.ost.audit.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.model.EntityType;
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

    private final AuditDomainHook auditDomainHook;

    Map<Long, String> resolveNames(@NonNull Set<Long> ids) {
        return auditDomainHook.resolveNames(ids);
    }

    Set<Long> findExisting(@NonNull EntityType entityType, @NonNull Set<Long> ids) {
        return auditDomainHook.findExisting(entityType, ids);
    }

    <T> List<T> withResolvedActorNames(List<T> items,
            Function<T, Long> actorIdGetter,
            BiFunction<T, String, T> nameApplier) {
        Set<Long> ids = items.stream()
                .map(actorIdGetter)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return items;
        Map<Long, String> names = resolveNames(ids);
        return items.stream()
                .map(i -> {
                    Long actorId = actorIdGetter.apply(i);
                    return actorId != null ? nameApplier.apply(i, names.getOrDefault(actorId, "—")) : i;
                })
                .toList();
    }
}
