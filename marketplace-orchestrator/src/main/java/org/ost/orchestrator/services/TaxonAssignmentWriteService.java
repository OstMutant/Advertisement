package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /** {@link #replace} diff-replaces ALL taxon types for the entity at once -- a nullable single city id must be unioned into the category-id set before one {@link #replace} call, never written as a separate call. */
    public static Set<Long> unionAssignmentIds(@NonNull Set<Long> ids, Long extraId) {
        return extraId != null
                ? Stream.concat(ids.stream(), Stream.of(extraId)).collect(Collectors.toSet())
                : ids;
    }
}
