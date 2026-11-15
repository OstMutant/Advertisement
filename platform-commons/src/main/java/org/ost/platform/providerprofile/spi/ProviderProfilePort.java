package org.ost.platform.providerprofile.spi;

import lombok.NonNull;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileScope;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * CRUD provider profiles (MASTER/SHOP/SUPPORT kinds), filtered/paginated queries, and the
 * purge-safety helper (findOwnerIds) used when a user account is deleted.
 * Implementation lives in provider-profile-spring-boot-starter.
 */
public interface ProviderProfilePort {

    default List<ProviderProfileDto> getFiltered(@NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort) {
        return getFiltered(filter, page, size, sort, ProviderProfileScope.ALL);
    }

    /** Like the plain variant, limited and ordered by the scope: only the ids it names (null names none and restricts nothing, an empty set matches nothing), its pinned groups before (or after) every other profile. */
    List<ProviderProfileDto> getFiltered(@NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort, @NonNull ProviderProfileScope scope);

    default int count(@NonNull ProviderProfileFilterDto filter) {
        return count(filter, ProviderProfileScope.ALL);
    }

    int count(@NonNull ProviderProfileFilterDto filter, @NonNull ProviderProfileScope scope);

    Optional<ProviderProfileDto> findById(@NonNull Long id);

    Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId);

    /** {@code targetUserId} owns the profile, {@code actingUserId} is the auditing actor; {@code kind == SUPPORT} needs {@code actingUserIsPrivileged}, else {@link IllegalStateException}. */
    Long save(@NonNull ProviderProfileSaveDto dto, @NonNull Long targetUserId, @NonNull Long actingUserId, boolean actingUserIsPrivileged);

    /** {@code version} must be the value last read; a stale value throws {@link org.ost.platform.core.StaleWriteException}. */
    void delete(@NonNull Long id, Long version);

    Set<Long> findExistingIds(@NonNull Set<Long> ids);

    /** Subset of {@code userIds} that own a provider profile. */
    Set<Long> findOwnerIds(@NonNull Set<Long> userIds);
}
