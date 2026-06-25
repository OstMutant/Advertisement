package org.ost.marketplace.services.audit.taxon;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.CurrentActorHook;
import org.ost.platform.taxon.dto.CategoryChangeSnapshotDto;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.spi.TaxonAuditHook;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TaxonActivityService {

    private final ComponentFactory<AuditPort> auditPortFactory;
    private final ComponentFactory<TaxonPort> taxonPortFactory;
    private final CurrentActorHook            currentActorHook;

    public void recordAssignmentChange(@NonNull EntityType entityType, @NonNull Long entityId,
                                        @NonNull Long taxonId,
                                        @NonNull TaxonAuditHook.AssignmentChange change) {
        currentActorHook.getCurrentActorId().ifPresent(actorId ->
                auditPortFactory.ifAvailable(port -> {
                    String categoryName = resolveCategoryName(taxonId);
                    boolean assigned = change == TaxonAuditHook.AssignmentChange.ASSIGNED;
                    CategoryChangeSnapshotDto before = new CategoryChangeSnapshotDto(entityType, categoryName, !assigned);
                    CategoryChangeSnapshotDto after  = new CategoryChangeSnapshotDto(entityType, categoryName, assigned);
                    port.captureUpdate(entityId, before, after, actorId);
                })
        );
    }

    private String resolveCategoryName(Long taxonId) {
        return taxonPortFactory.findIfAvailable()
                .flatMap(p -> p.findById(taxonId, Locale.ENGLISH))
                .map(TaxonDto::getName)
                .filter(name -> !name.isBlank())
                .orElse(String.valueOf(taxonId));
    }
}
