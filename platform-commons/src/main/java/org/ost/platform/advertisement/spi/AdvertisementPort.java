package org.ost.platform.advertisement.spi;

import lombok.NonNull;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port: marketplace → advertisement-starter.
 * CRUD advertisements, filtered/paginated queries, ownership checks, and purge-safety helpers
 * (findOwnerIds/clearActorReferences) used when a user account is deleted.
 * Implementation lives in advertisement-spring-boot-starter.
 */
public interface AdvertisementPort {

    List<AdvertisementInfoDto> getFiltered(@NonNull AdvertisementFilterDto filter, int page, int size, @NonNull Sort sort);

    int count(@NonNull AdvertisementFilterDto filter);

    Optional<AdvertisementInfoDto> findById(@NonNull Long id);

    Long save(@NonNull AdvertisementSaveDto dto);

    /** {@code version} must be the value the caller last read; a stale value throws
     *  OptimisticLockingFailureException. */
    void delete(@NonNull Long id, @NonNull Long actingUserId, Long version);

    Set<Long> findExistingIds(@NonNull Set<Long> ids);

    List<AdvertisementInfoDto> findByCreator(@NonNull Long userId);

    /** Subset of {@code userIds} that own at least one advertisement (including soft-deleted rows). */
    Set<Long> findOwnerIds(@NonNull Set<Long> userIds);

    /** Nulls {@code updated_by}/{@code deleted_by} on every advertisement referencing any of {@code userIds}. */
    void clearActorReferences(@NonNull Set<Long> userIds);
}
