package org.ost.orchestrator.shared;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared read-only {@link TaxonPort} lookups reused by every domain's display-enrichment step. */
@Service
@RequiredArgsConstructor
public class TaxonLookupService {

    private final ComponentFactory<TaxonPort> taxonPortFactory;

    public Map<Long, List<TaxonDto>> getForEntities(@NonNull EntityType entityType, @NonNull Set<Long> entityIds,
                                                     @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.getForEntities(entityType, entityIds, locale))
                .orElse(Map.of());
    }

    public List<TaxonDto> getForEntity(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.getForEntity(entityType, entityId, locale))
                .orElse(List.of());
    }

    public Map<Long, TaxonDto> findByIds(@NonNull Set<Long> taxonIds, @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.findByIds(taxonIds, locale))
                .orElse(Map.of());
    }

    public Optional<TaxonDto> findById(@NonNull Long taxonId, @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable().flatMap(p -> p.findById(taxonId, locale));
    }
}
