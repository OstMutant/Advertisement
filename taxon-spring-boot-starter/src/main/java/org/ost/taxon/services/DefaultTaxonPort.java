package org.ost.taxon.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.taxon.config.TaxonProperties;
import org.ost.taxon.entities.Taxon;
import org.ost.taxon.entities.TaxonAssignment;
import org.ost.taxon.entities.TaxonTranslation;
import org.ost.taxon.repository.TaxonFilter;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultTaxonPort implements TaxonPort {

    private final TaxonService           taxonService;
    private final TaxonAssignmentService assignmentService;
    private final TaxonProperties        properties;

    @Override
    @Transactional
    public void replaceAssignments(@NonNull EntityType entityType, @NonNull Long entityId,
                                   @NonNull Set<Long> taxonIds) {
        assignmentService.replaceAssignments(entityType, entityId, taxonIds, null);
    }

    @Override
    public List<TaxonDto> getForEntity(@NonNull EntityType entityType, @NonNull Long entityId,
                                       @NonNull Locale locale) {
        List<TaxonAssignment> assignments = assignmentService.getForEntity(entityType, entityId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        return resolveDtos(assignments.stream().map(TaxonAssignment::getTaxonId).toList(), locale, true);
    }

    @Override
    public Map<Long, List<TaxonDto>> getForEntities(@NonNull EntityType entityType,
                                                     @NonNull Set<Long> entityIds,
                                                     @NonNull Locale locale) {
        Map<Long, List<TaxonAssignment>> byEntity = assignmentService.getForEntities(entityType, entityIds);
        Set<Long> allTaxonIds = byEntity.values().stream()
                .flatMap(Collection::stream)
                .map(TaxonAssignment::getTaxonId)
                .collect(Collectors.toSet());

        Map<Long, TaxonDto> dtoById = allTaxonIds.isEmpty()
                ? Map.of()
                : buildDtoIndex(allTaxonIds.stream().toList(), locale, true);

        return entityIds.stream().collect(Collectors.toMap(
                eid -> eid,
                eid -> byEntity.getOrDefault(eid, List.of()).stream()
                        .map(a -> dtoById.get(a.getTaxonId()))
                        .filter(dto -> dto != null)
                        .toList()
        ));
    }

    @Override
    public List<TaxonDto> getAllByType(@NonNull TaxonType type, @NonNull Locale locale) {
        List<Taxon> taxa = taxonService.listByType(type, TaxonFilter.active(), Sort.by("id"));
        if (taxa.isEmpty()) {
            return List.of();
        }
        return resolveDtos(taxa.stream().map(Taxon::getId).toList(), locale, false);
    }

    @Override
    public Optional<TaxonDto> findById(@NonNull Long taxonId, @NonNull Locale locale) {
        return taxonService.findById(taxonId)
                .map(taxon -> toDto(taxon, locale));
    }

    @Override
    public Map<Long, TaxonDto> findByIds(@NonNull Set<Long> taxonIds, @NonNull Locale locale) {
        if (taxonIds.isEmpty()) {
            return Map.of();
        }
        return buildDtoIndex(taxonIds.stream().toList(), locale, false);
    }

    @Override
    public Set<Long> findEntityIdsWithAnyTaxon(@NonNull EntityType entityType,
                                                @NonNull Set<Long> taxonIds) {
        return assignmentService.findEntityIdsByTaxonIds(entityType, taxonIds);
    }

    // ── Management operations ───────────────────────────────────────────────

    @Override
    public List<TaxonDto> listAllByType(@NonNull TaxonType type, @NonNull Locale locale, boolean includeDeleted) {
        List<Taxon> taxa = taxonService.listByType(type, TaxonFilter.of(null, includeDeleted), Sort.by("id"));
        if (taxa.isEmpty()) {
            return List.of();
        }
        List<Long> ids = taxa.stream().map(Taxon::getId).toList();
        Map<Long, List<TaxonTranslation>> byTaxon = taxonService.getTranslationsForMany(ids)
                .stream()
                .collect(Collectors.groupingBy(TaxonTranslation::getTaxonId));
        return taxa.stream()
                .map(t -> toDto(t, byTaxon.getOrDefault(t.getId(), List.of()), locale))
                .toList();
    }

    @Override
    public List<TaxonTranslationDto> getTranslations(@NonNull Long taxonId) {
        return taxonService.getTranslations(taxonId).stream()
                .map(t -> TaxonTranslationDto.builder()
                        .locale(t.getLocale())
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .toList();
    }

    @Override
    public Map<Long, Long> getUsageCounts(@NonNull TaxonType type) {
        List<Taxon> taxa = taxonService.listByType(type, TaxonFilter.all(), Sort.unsorted());
        if (taxa.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = taxa.stream().map(Taxon::getId).collect(Collectors.toSet());
        return assignmentService.countByTaxonIds(ids);
    }

    @Override
    @Transactional
    public Long create(@NonNull TaxonType type, @NonNull Map<Locale, TaxonTranslationDto> translations,
                       Long actorId) {
        Map<Locale, TaxonTranslationData> data = toTranslationData(translations);
        return taxonService.create(type, null, data, actorId).getId();
    }

    @Override
    @Transactional
    public void update(@NonNull Long id, @NonNull Map<Locale, TaxonTranslationDto> translations,
                       Long actorId, Long version) {
        Map<Locale, TaxonTranslationData> data = toTranslationData(translations);
        taxonService.update(id, data, actorId, version);
    }

    @Override
    @Transactional
    public void softDelete(@NonNull Long id, Long actorId, Long version) {
        taxonService.softDelete(id, actorId, version);
    }

    @Override
    @Transactional
    public void restore(@NonNull Long id, Long actorId) {
        taxonService.restore(id, actorId);
    }

    @Override
    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return taxonService.findExistingIds(ids);
    }

    // ── internal helpers ───────────────────────────────────────────────────

    private List<TaxonDto> resolveDtos(List<Long> taxonIds, Locale locale, boolean activeOnly) {
        Map<Long, Taxon> byId = indexById(taxonIds);
        Map<Long, List<TaxonTranslation>> byTaxon = taxonService.getTranslationsForMany(taxonIds)
                .stream()
                .collect(Collectors.groupingBy(TaxonTranslation::getTaxonId));
        return taxonIds.stream()
                .map(byId::get)
                .filter(t -> t != null && (!activeOnly || t.getDeletedAt() == null))
                .map(t -> toDto(t, byTaxon.getOrDefault(t.getId(), List.of()), locale))
                .toList();
    }

    private Map<Long, TaxonDto> buildDtoIndex(List<Long> taxonIds, Locale locale, boolean activeOnly) {
        Map<Long, Taxon> byId = indexById(taxonIds);
        Map<Long, List<TaxonTranslation>> byTaxon = taxonService.getTranslationsForMany(taxonIds)
                .stream()
                .collect(Collectors.groupingBy(TaxonTranslation::getTaxonId));
        return byId.values().stream()
                .filter(t -> !activeOnly || t.getDeletedAt() == null)
                .collect(Collectors.toMap(
                        Taxon::getId,
                        t -> toDto(t, byTaxon.getOrDefault(t.getId(), List.of()), locale)
                ));
    }

    private Map<Long, Taxon> indexById(List<Long> taxonIds) {
        return taxonService.findByIds(Set.copyOf(taxonIds)).stream()
                .collect(Collectors.toMap(Taxon::getId, t -> t));
    }

    private TaxonDto toDto(Taxon taxon, Locale locale) {
        List<TaxonTranslation> translations = taxonService.getTranslations(taxon.getId());
        return toDto(taxon, translations, locale);
    }

    private TaxonDto toDto(Taxon taxon, List<TaxonTranslation> translations, Locale locale) {
        TaxonTranslation tr = resolveTranslation(translations, locale);
        String name        = tr != null ? tr.getName()        : "";
        String description = tr != null ? tr.getDescription() : "";
        return TaxonDto.builder()
                .id(taxon.getId())
                .type(taxon.getType())
                .code(taxon.getCode())
                .name(name)
                .description(description)
                .deleted(taxon.getDeletedAt() != null)
                .version(taxon.getVersion())
                .build();
    }

    private Map<Locale, TaxonTranslationData> toTranslationData(Map<Locale, TaxonTranslationDto> translations) {
        return translations.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new TaxonTranslationData(e.getValue().getName(), e.getValue().getDescription())
                ));
    }

    TaxonTranslation resolveTranslation(List<TaxonTranslation> translations, Locale locale) {
        return translations.stream()
                .filter(t -> t.getLocale().equals(locale.getLanguage()))
                .findFirst()
                .or(() -> translations.stream()
                        .filter(t -> t.getLocale().equals(properties.defaultLocale().getLanguage()))
                        .findFirst())
                .or(() -> translations.stream().findFirst())
                .orElse(null);
    }
}
