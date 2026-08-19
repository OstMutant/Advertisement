package org.ost.platform.providerprofile.spi;

import lombok.NonNull;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port: marketplace → provider-profile-starter.
 * CRUD provider profiles (MASTER/SHOP/SUPPORT kinds), filtered/paginated queries, and the
 * purge-safety helper (findOwnerIds) used when a user account is deleted.
 * Implementation lives in provider-profile-spring-boot-starter.
 */
public interface ProviderProfilePort {

    List<ProviderProfileDto> getFiltered(@NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort);

    int count(@NonNull ProviderProfileFilterDto filter);

    Optional<ProviderProfileDto> findById(@NonNull Long id);

    Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId);

    /** {@code actingUserIsPrivileged} gates {@code kind == SUPPORT}; throws
     *  {@link IllegalStateException} otherwise. */
    Long save(@NonNull ProviderProfileSaveDto dto, @NonNull Long actingUserId, boolean actingUserIsPrivileged);

    /** {@code version} must be the value the caller last read; a stale value throws
     *  OptimisticLockingFailureException. */
    void delete(@NonNull Long id, Long version);

    Set<Long> findExistingIds(@NonNull Set<Long> ids);

    /** Subset of {@code userIds} that own a provider profile. */
    Set<Long> findOwnerIds(@NonNull Set<Long> userIds);
}
