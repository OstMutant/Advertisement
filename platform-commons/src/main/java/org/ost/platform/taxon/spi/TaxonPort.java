package org.ost.platform.taxon.spi;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.model.TaxonType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Port: marketplace → taxon-starter.
 * Marketplace calls this to manage taxon assignments and resolve localised taxon data.
 * Injected via {@code ObjectProvider} — degrades gracefully when the starter is absent.
 */
public interface TaxonPort {

    /** Assigns a taxon entry to an entity (idempotent). */
    void assign(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long taxonId);

    /** Removes a taxon assignment (no-op if absent). */
    void unassign(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long taxonId);

    /** Replaces all taxon assignments for an entity with the given set in one transaction. */
    void replaceAssignments(@NonNull EntityType entityType, @NonNull Long entityId,
                            @NonNull Set<Long> taxonIds);

    /** Active taxon entries currently assigned to an entity, localised to the given locale. */
    List<TaxonDto> getForEntity(@NonNull EntityType entityType, @NonNull Long entityId,
                                @NonNull Locale locale);

    /**
     * Batched variant: returns assignments for many entities in one call.
     * Avoids N+1 when rendering card lists.
     */
    Map<Long, List<TaxonDto>> getForEntities(@NonNull EntityType entityType,
                                             @NonNull Set<Long> entityIds,
                                             @NonNull Locale locale);

    /** All active entries of a given taxon type, localised. */
    List<TaxonDto> getAllByType(@NonNull TaxonType type, @NonNull Locale locale);

    /** Resolves a specific entry by id (even if soft-deleted — used by audit rendering). */
    Optional<TaxonDto> findById(@NonNull Long taxonId, @NonNull Locale locale);

    /** Resolves a well-known entry by its stable code. */
    Optional<TaxonDto> findByCode(@NonNull TaxonType type, @NonNull String code,
                                  @NonNull Locale locale);

    /**
     * Returns the set of entity ids that have AT LEAST ONE of the given taxons assigned.
     * Used by marketplace to filter advertisements without exposing taxon_assignment table names.
     * Empty input set → empty result.
     */
    Set<Long> findEntityIdsWithAnyTaxon(@NonNull EntityType entityType,
                                        @NonNull Set<Long> taxonIds);

    // ── Management operations ───────────────────────────────────────────────

    /** All entries of a given type, optionally including soft-deleted ones, localised. */
    List<TaxonDto> listAllByType(@NonNull TaxonType type, @NonNull Locale locale, boolean includeDeleted);

    /** All locale translations for a given taxon entry. Used by the edit overlay. */
    List<TaxonTranslationDto> getTranslations(@NonNull Long taxonId);

    /** Usage counts (assignment count) per taxon id for all entries of a given type. */
    Map<Long, Long> getUsageCounts(@NonNull TaxonType type);

    /** Creates a new taxon entry with the given translations. Returns the new entry's id. */
    Long create(@NonNull TaxonType type, @NonNull Map<Locale, TaxonTranslationDto> translations, Long actorId);

    /** Updates all translations for the given taxon entry. */
    void update(@NonNull Long id, @NonNull Map<Locale, TaxonTranslationDto> translations, Long actorId);

    /** Soft-deletes the given taxon entry. */
    void softDelete(@NonNull Long id);

    /** Restores a soft-deleted taxon entry. */
    void restore(@NonNull Long id);

    /** Returns the subset of the given ids that exist in the taxon table. Used by audit findExisting. */
    Set<Long> findExistingIds(@NonNull Set<Long> ids);
}
