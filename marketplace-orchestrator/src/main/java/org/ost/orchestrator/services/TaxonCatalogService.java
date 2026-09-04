package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonFilterDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared {@link TaxonPort} catalog-management lookups and privileged-only writes (category/city
 * admin), reused by every marketplace-app adapter. Distinct from {@link TaxonLookupService}, which
 * stays narrowly scoped to entity-assignment lookups.
 */
@Service
@RequiredArgsConstructor
public class TaxonCatalogService {

    private final ComponentFactory<TaxonPort> taxonPortFactory;
    private final AuthorizationService        authorizationService;

    public List<TaxonDto> getAllByType(@NonNull TaxonType type, @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.getAllByType(type, locale))
                .orElse(List.of());
    }

    public List<TaxonDto> listAllByType(@NonNull TaxonType type, @NonNull Locale locale, boolean includeDeleted) {
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.listAllByType(type, locale, includeDeleted))
                .orElse(List.of());
    }

    public Map<Long, Long> getUsageCounts(@NonNull TaxonType type) {
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.getUsageCounts(type))
                .orElse(Map.of());
    }

    public List<TaxonDto> getPage(@NonNull TaxonType type, @NonNull Locale locale, @NonNull TaxonFilterDto filter,
                                  int page, int size, @NonNull Sort sort) {
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.getPageByType(type, locale, filter, page, size, sort))
                .orElse(List.of());
    }

    public int count(@NonNull TaxonType type, @NonNull TaxonFilterDto filter) {
        return taxonPortFactory.findIfAvailable().map(p -> p.count(type, filter)).orElse(0);
    }

    public Long create(@NonNull TaxonType type, @NonNull Map<Locale, TaxonTranslationDto> translations, Long actorId) {
        authorizationService.requireIsPrivileged(actorId);
        return taxonPortFactory.get().create(type, translations, actorId);
    }

    public void update(@NonNull Long id, @NonNull Map<Locale, TaxonTranslationDto> translations, Long actorId, Long version) {
        authorizationService.requireIsPrivileged(actorId);
        taxonPortFactory.get().update(id, translations, actorId, version);
    }

    public void softDelete(@NonNull Long id, Long actorId, Long version) {
        authorizationService.requireIsPrivileged(actorId);
        taxonPortFactory.ifAvailable(p -> p.softDelete(id, actorId, version));
    }

    public void restore(@NonNull Long id, Long actorId) {
        authorizationService.requireIsPrivileged(actorId);
        taxonPortFactory.ifAvailable(p -> p.restore(id, actorId));
    }

    public Optional<TaxonDto> findById(@NonNull Long taxonId, @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable().flatMap(p -> p.findById(taxonId, locale));
    }

    public List<TaxonTranslationDto> getTranslations(@NonNull Long taxonId) {
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.getTranslations(taxonId))
                .orElse(List.of());
    }

    public boolean isAvailable() {
        return taxonPortFactory.findIfAvailable().isPresent();
    }
}
