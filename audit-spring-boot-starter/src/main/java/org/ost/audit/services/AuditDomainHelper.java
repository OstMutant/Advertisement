package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

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
}
