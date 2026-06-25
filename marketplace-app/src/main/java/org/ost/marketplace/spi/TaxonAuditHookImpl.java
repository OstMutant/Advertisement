package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.audit.taxon.TaxonActivityService;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.spi.TaxonAuditHook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaxonAuditHookImpl implements TaxonAuditHook {

    private final TaxonActivityService taxonActivityService;

    @Override
    public void onAssignmentChanged(@NonNull EntityType entityType, @NonNull Long entityId,
                                    @NonNull Long taxonId, @NonNull AssignmentChange change) {
        taxonActivityService.recordAssignmentChange(entityType, entityId, taxonId, change);
    }
}
