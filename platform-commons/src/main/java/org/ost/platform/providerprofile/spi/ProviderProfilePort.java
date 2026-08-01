package org.ost.platform.providerprofile.spi;

import lombok.NonNull;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public interface ProviderProfilePort {

    List<ProviderProfileDto> getFiltered(@NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort, @NonNull Locale locale);

    int count(@NonNull ProviderProfileFilterDto filter);

    Optional<ProviderProfileDto> findById(@NonNull Long id, @NonNull Locale locale);

    Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId, @NonNull Locale locale);

    /** {@code actingUserIsPrivileged} gates {@code kind == SUPPORT}; throws
     *  {@link IllegalStateException} otherwise. */
    Long save(@NonNull ProviderProfileSaveDto dto, @NonNull Long actingUserId, boolean actingUserIsPrivileged);

    /** {@code version} must be the value the caller last read; a stale value throws
     *  {@link org.springframework.dao.OptimisticLockingFailureException}. */
    void delete(@NonNull Long id, Long version);

    Set<Long> findExistingIds(@NonNull Set<Long> ids);

    /** Subset of {@code userIds} that own a provider profile. */
    Set<Long> findOwnerIds(@NonNull Set<Long> userIds);
}
