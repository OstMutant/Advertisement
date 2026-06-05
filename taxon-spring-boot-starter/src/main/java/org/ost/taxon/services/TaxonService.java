package org.ost.taxon.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.taxon.config.TaxonProperties;
import org.ost.taxon.entities.Taxon;
import org.ost.taxon.entities.TaxonTranslation;
import org.ost.taxon.repository.TaxonFilter;
import org.ost.taxon.repository.TaxonRepository;
import org.ost.taxon.repository.TaxonTranslationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaxonService {

    private final TaxonRepository            taxonRepository;
    private final TaxonTranslationRepository translationRepository;
    private final TaxonProperties            properties;

    @Transactional
    public Taxon create(@NonNull TaxonType type, String code,
                        @NonNull Map<Locale, TaxonTranslationData> translations,
                        Long actorId) {
        validateTranslations(translations);
        Taxon taxon = taxonRepository.save(Taxon.builder()
                .type(type)
                .code(code)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build());
        translationRepository.saveAll(taxon.getId(), toEntities(taxon.getId(), translations));
        return taxon;
    }

    @Transactional
    public Taxon update(@NonNull Long id, @NonNull Map<Locale, TaxonTranslationData> translations,
                        Long actorId) {
        validateTranslations(translations);
        Taxon existing = taxonRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Taxon not found: " + id));
        Taxon updated = Taxon.builder()
                .id(existing.getId())
                .type(existing.getType())
                .code(existing.getCode())
                .deletedAt(existing.getDeletedAt())
                .createdAt(existing.getCreatedAt())
                .createdBy(existing.getCreatedBy())
                .updatedAt(Instant.now())
                .updatedBy(actorId)
                .build();
        taxonRepository.save(updated);
        translationRepository.saveAll(id, toEntities(id, translations));
        return updated;
    }

    public void softDelete(@NonNull Long id) {
        taxonRepository.softDelete(id);
    }

    public void restore(@NonNull Long id) {
        taxonRepository.restore(id);
    }

    public List<Taxon> listByType(@NonNull TaxonType type, @NonNull TaxonFilter filter, @NonNull Sort sort) {
        return taxonRepository.findAllByType(type, filter, sort);
    }

    public Optional<Taxon> findById(@NonNull Long id) {
        return taxonRepository.findById(id);
    }

    public Optional<Taxon> findByCode(@NonNull TaxonType type, @NonNull String code) {
        return taxonRepository.findByTypeAndCode(type, code);
    }

    public List<TaxonTranslation> getTranslations(@NonNull Long taxonId) {
        return translationRepository.findAllByTaxonId(taxonId);
    }

    public List<TaxonTranslation> getTranslationsForMany(@NonNull List<Long> taxonIds) {
        return translationRepository.findAllByTaxonIds(taxonIds);
    }

    private void validateTranslations(@NonNull Map<Locale, TaxonTranslationData> translations) {
        for (Locale locale : properties.supportedLocales()) {
            TaxonTranslationData data = translations.get(locale);
            if (data == null || data.name().isBlank() || data.description().isBlank()) {
                throw new IllegalArgumentException(
                        "Translation for locale '%s' is missing or incomplete".formatted(locale));
            }
        }
    }

    private List<TaxonTranslation> toEntities(Long taxonId, Map<Locale, TaxonTranslationData> translations) {
        return translations.entrySet().stream()
                .map(e -> TaxonTranslation.builder()
                        .taxonId(taxonId)
                        .locale(e.getKey().toLanguageTag())
                        .name(e.getValue().name())
                        .description(e.getValue().description())
                        .build())
                .toList();
    }
}
