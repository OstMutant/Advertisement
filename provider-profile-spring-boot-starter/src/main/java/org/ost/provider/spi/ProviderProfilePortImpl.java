package org.ost.provider.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.ost.provider.services.ProviderProfileService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderProfilePortImpl implements ProviderProfilePort {

    private final ProviderProfileService service;

    @Override
    public List<ProviderProfileDto> getFiltered(@NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort) {
        return service.getFiltered(filter, page, size, sort);
    }

    @Override
    public int count(@NonNull ProviderProfileFilterDto filter) {
        return service.count(filter);
    }

    @Override
    public Optional<ProviderProfileDto> findById(@NonNull Long id) {
        return service.findById(id);
    }

    @Override
    public Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId) {
        return service.findByActorId(actorId);
    }

    @Override
    @Transactional
    public Long save(@NonNull ProviderProfileSaveDto dto, @NonNull Long actingUserId, boolean actingUserIsPrivileged) {
        return service.save(dto, actingUserId, actingUserIsPrivileged);
    }

    @Override
    @Transactional
    public void delete(@NonNull Long id, Long version) {
        service.delete(id, version);
    }

    @Override
    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return service.findExistingIds(ids);
    }

    @Override
    public Set<Long> findOwnerIds(@NonNull Set<Long> userIds) {
        return service.findOwnerIds(userIds);
    }
}
