package org.ost.taxon.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.taxon.config.TaxonProperties;
import org.ost.taxon.entities.Taxon;
import org.ost.taxon.entities.TaxonAssignment;
import org.ost.taxon.entities.TaxonTranslation;
import org.ost.taxon.repository.TaxonFilter;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Facade over TaxonService and TaxonAssignmentService.
 * Will implement TaxonPort (from platform-commons) once the SPI surface is added in Step 4.
 */
@Service
@RequiredArgsConstructor
public class DefaultTaxonPort {

    private final TaxonService            taxonService;
    private final TaxonAssignmentService  assignmentService;
    private final TaxonProperties         properties;

    public void assign(@NonNull EntityType entityType, @NonNull Long entityId,
                       @NonNull Long taxonId, Long actorId) {
        assignmentService.assign(entityType, entityId, taxonId, actorId);
    }

    public void unassign(@NonNull EntityType entityType, @NonNull Long entityId,
                         @NonNull Long taxonId, Long actorId) {
        assignmentService.unassign(entityType, entityId, taxonId, actorId);
    }

    public void replaceAssignments(@NonNull EntityType entityType, @NonNull Long entityId,
                                   @NonNull Set<Long> taxonIds, Long actorId) {
        assignmentService.replaceAssignments(entityType, entityId, taxonIds, actorId);
    }

    public List<Taxon> getForEntity(@NonNull EntityType entityType, @NonNull Long entityId,
                                    @NonNull Locale locale) {
        List<TaxonAssignment> assignments = assignmentService.getForEntity(entityType, entityId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<Long> taxonIds = assignments.stream().map(TaxonAssignment::getTaxonId).toList();
        Map<Long, List<TaxonTranslation>> translationsByTaxonId = taxonService.getTranslationsForMany(taxonIds)
                .stream()
                .collect(Collectors.groupingBy(TaxonTranslation::getTaxonId));
        return taxonIds.stream()
                .map(id -> taxonService.findById(id).orElse(null))
                .filter(t -> t != null && t.getDeletedAt() == null)
                .toList();
    }

    public Map<Long, List<Taxon>> getForEntities(@NonNull EntityType entityType,
                                                  @NonNull Set<Long> entityIds,
                                                  @NonNull Locale locale) {
        Map<Long, List<TaxonAssignment>> byEntity = assignmentService.getForEntities(entityType, entityIds);
        Set<Long> allTaxonIds = byEntity.values().stream()
                .flatMap(List::stream)
                .map(TaxonAssignment::getTaxonId)
                .collect(Collectors.toSet());
        if (allTaxonIds.isEmpty()) {
            return entityIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
        }
        Map<Long, Taxon> taxonById = taxonService.getTranslationsForMany(allTaxonIds.stream().toList())
                .stream()
                .collect(Collectors.toMap(TaxonTranslation::getTaxonId, t -> t, (a, b) -> a))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> taxonService.findById(e.getKey()).orElse(null)));
        return entityIds.stream().collect(Collectors.toMap(
                eid -> eid,
                eid -> byEntity.getOrDefault(eid, List.of()).stream()
                        .map(a -> taxonById.get(a.getTaxonId()))
                        .filter(t -> t != null && t.getDeletedAt() == null)
                        .toList()
        ));
    }

    public List<Taxon> getAllByType(@NonNull TaxonType type, @NonNull Locale locale) {
        return taxonService.listByType(type, TaxonFilter.active(), Sort.by("id"));
    }

    public Optional<Taxon> findById(@NonNull Long taxonId, @NonNull Locale locale) {
        return taxonService.findById(taxonId);
    }

    public Optional<Taxon> findByCode(@NonNull TaxonType type, @NonNull String code,
                                       @NonNull Locale locale) {
        return taxonService.findByCode(type, code);
    }

    public Set<Long> findEntityIdsWithAnyTaxon(@NonNull EntityType entityType,
                                                @NonNull Set<Long> taxonIds) {
        return assignmentService.findEntityIdsByTaxonIds(entityType, taxonIds);
    }

    /** Resolves the best-fit translation for a taxon given the requested locale. */
    TaxonTranslation resolveTranslation(@NonNull List<TaxonTranslation> translations,
                                         @NonNull Locale locale) {
        return translations.stream()
                .filter(t -> t.getLocale().equals(locale.toLanguageTag()))
                .findFirst()
                .or(() -> translations.stream()
                        .filter(t -> t.getLocale().equals(properties.defaultLocale().toLanguageTag()))
                        .findFirst())
                .or(() -> translations.stream().findFirst())
                .orElse(null);
    }
}
