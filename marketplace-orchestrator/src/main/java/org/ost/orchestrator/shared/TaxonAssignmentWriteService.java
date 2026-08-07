package org.ost.orchestrator.shared;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Shared {@link TaxonPort#replaceAssignments} write, reused by every domain's save/delete path. */
@Service
@RequiredArgsConstructor
public class TaxonAssignmentWriteService {

    private final ComponentFactory<TaxonPort> taxonPortFactory;

    public void replace(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Set<Long> taxonIds) {
        taxonPortFactory.ifAvailable(p -> p.replaceAssignments(entityType, entityId, taxonIds));
    }

    public void clear(@NonNull EntityType entityType, @NonNull Long entityId) {
        replace(entityType, entityId, Set.of());
    }
}
