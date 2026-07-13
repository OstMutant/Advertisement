package org.ost.taxon.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaxonService {

    private final TaxonRepository            taxonRepository;
    private final TaxonTranslationRepository translationRepository;
    private final TaxonProperties            properties;
    private final ComponentFactory<AuditPort> auditPortFactory;

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
        TaxonSnapshotDto snapshot = buildSnapshotFromData(translations);
        if (actorId != null) {
            auditPortFactory.ifAvailable(p -> p.captureCreation(taxon.getId(), snapshot, actorId));
        }
        return taxon;
    }

    @Transactional
    public Taxon update(@NonNull Long id, @NonNull Map<Locale, TaxonTranslationData> translations,
                        Long actorId, Long version) {
        validateTranslations(translations);
        Taxon existing = taxonRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Taxon not found: " + id));
        List<TaxonTranslation> beforeTranslations = translationRepository.findAllByTaxonId(id);
        TaxonSnapshotDto beforeSnapshot = buildSnapshotFromTranslations(beforeTranslations);
        Taxon updated = Taxon.builder()
                .id(existing.getId())
                .type(existing.getType())
                .code(existing.getCode())
                .deletedAt(existing.getDeletedAt())
                .createdAt(existing.getCreatedAt())
                .createdBy(existing.getCreatedBy())
                .updatedAt(Instant.now())
                .updatedBy(actorId)
                .version(version)
                .build();
        taxonRepository.save(updated);
        translationRepository.saveAll(id, toEntities(id, translations));
        TaxonSnapshotDto afterSnapshot = buildSnapshotFromData(translations);
        if (actorId != null) {
            auditPortFactory.ifAvailable(p -> p.captureUpdate(id, beforeSnapshot, afterSnapshot, actorId));
        }
        return updated;
    }

    @Transactional
    public void softDelete(@NonNull Long id, Long actorId, Long version) {
        List<TaxonTranslation> translations = translationRepository.findAllByTaxonId(id);
        TaxonSnapshotDto snapshot = buildSnapshotFromTranslations(translations);
        taxonRepository.softDelete(id, actorId, version);
        if (actorId != null) {
            auditPortFactory.ifAvailable(p -> p.captureDeletion(id, snapshot, actorId));
        }
    }

    @Transactional
    public void restore(@NonNull Long id, Long actorId) {
        taxonRepository.restore(id);
        if (actorId != null) {
            List<TaxonTranslation> translations = translationRepository.findAllByTaxonId(id);
            TaxonSnapshotDto snapshot = buildSnapshotFromTranslations(translations);
            auditPortFactory.ifAvailable(p -> p.captureRestore(id, snapshot, actorId));
        }
    }

    public List<Taxon> listByType(@NonNull TaxonType type, @NonNull TaxonFilter filter, @NonNull Sort sort) {
        return taxonRepository.findAllByType(type, filter, sort);
    }

    public Optional<Taxon> findById(@NonNull Long id) {
        return taxonRepository.findById(id);
    }

    public List<Taxon> findByIds(@NonNull Set<Long> ids) {
        return taxonRepository.findByIds(ids);
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

    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return taxonRepository.findExistingIds(ids);
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

    private TaxonSnapshotDto buildSnapshotFromData(Map<Locale, TaxonTranslationData> translations) {
        TaxonTranslationData en = translations.get(Locale.ENGLISH);
        TaxonTranslationData uk = translations.get(Locale.forLanguageTag("uk"));
        return new TaxonSnapshotDto(
                en != null ? en.name()        : null,
                en != null ? en.description() : null,
                uk != null ? uk.name()        : null,
                uk != null ? uk.description() : null);
    }

    private TaxonSnapshotDto buildSnapshotFromTranslations(List<TaxonTranslation> translations) {
        String nameEn = null, descEn = null, nameUk = null, descUk = null;
        for (TaxonTranslation t : translations) {
            if ("en".equals(t.getLocale()))       { nameEn = t.getName(); descEn = t.getDescription(); }
            else if ("uk".equals(t.getLocale()))  { nameUk = t.getName(); descUk = t.getDescription(); }
        }
        return new TaxonSnapshotDto(nameEn, descEn, nameUk, descUk);
    }
}
