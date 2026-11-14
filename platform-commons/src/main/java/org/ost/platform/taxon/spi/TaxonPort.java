package org.ost.platform.taxon.spi;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.SqlColumn;
import org.ost.platform.core.model.SqlPredicate;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.model.TaxonType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Taxon catalog, assignment and filter-predicate contract; injected via {@code ObjectProvider} and degrades gracefully
 * when the starter is absent.
 */
public interface TaxonPort {

    /** Replaces all taxon assignments for an entity with the given set in one transaction. */
    void replaceAssignments(@NonNull EntityType entityType, @NonNull Long entityId,
                            @NonNull Set<Long> taxonIds);

    /** Taxon entries currently assigned to an entity, soft-deleted ones included, localised to the given locale. */
    List<TaxonDto> getForEntity(@NonNull EntityType entityType, @NonNull Long entityId,
                                @NonNull Locale locale);

    /** Batched variant of {@link #getForEntity}: the active assigned entries of many entities in one call. */
    Map<Long, List<TaxonDto>> getForEntities(@NonNull EntityType entityType,
                                             @NonNull Set<Long> entityIds,
                                             @NonNull Locale locale);

    /** All active entries of a given taxon type, localised. */
    List<TaxonDto> getAllByType(@NonNull TaxonType type, @NonNull Locale locale);

    /** Resolves a specific entry by id (even if soft-deleted — used by audit rendering). */
    Optional<TaxonDto> findById(@NonNull Long taxonId, @NonNull Locale locale);

    /**
     * Batched variant of {@link #findById}: resolves many entries by id in one call, soft-deleted ones included;
     * an empty input set gives an empty result.
     */
    Map<Long, TaxonDto> findByIds(@NonNull Set<Long> taxonIds, @NonNull Locale locale);

    /**
     * A predicate restricting a consumer's statement to the entities carrying one of the given categories and the given city,
     * built over the consumer's own key column and appended to its page and count statements; empty when neither filter is
     * requested, and matching nothing for a present but empty {@code categoryIds}.
     */
    Optional<SqlPredicate> categoryAndCityPredicate(@NonNull EntityType entityType, @NonNull SqlColumn entityId,
                                                    Set<Long> categoryIds, Long cityTaxonId);

    // ── Management operations ───────────────────────────────────────────────

    /** All entries of a given type, optionally including soft-deleted ones, localised. */
    List<TaxonDto> listAllByType(@NonNull TaxonType type, @NonNull Locale locale, boolean includeDeleted);

    /** All locale translations for a given taxon entry. */
    List<TaxonTranslationDto> getTranslations(@NonNull Long taxonId);

    /** Usage counts (assignment count) per taxon id for all entries of a given type. */
    Map<Long, Long> getUsageCounts(@NonNull TaxonType type);

    /** Creates a new taxon entry with the given translations. Returns the new entry's id. */
    Long create(@NonNull TaxonType type, @NonNull Map<Locale, TaxonTranslationDto> translations, Long actorId);

    /** Updates all translations for the given taxon entry. {@code version} must be the value the
     *  caller last read; a stale value throws {@link org.ost.platform.core.StaleWriteException}. */
    void update(@NonNull Long id, @NonNull Map<Locale, TaxonTranslationDto> translations, Long actorId, Long version);

    /** Soft-deletes the given taxon entry and, when an actor id is given and the audit starter is present, records the
     *  deletion in the audit log. {@code version}
     *  must be the value the caller last read; a stale value throws {@link org.ost.platform.core.StaleWriteException}. */
    void softDelete(@NonNull Long id, Long actorId, Long version);

    /** Restores a soft-deleted taxon entry and, when an actor id is given and the audit starter is present, records the restore in the audit log. */
    void restore(@NonNull Long id, Long actorId);

    /** Returns the subset of the given ids that exist in the taxon table. */
    Set<Long> findExistingIds(@NonNull Set<Long> ids);
}
