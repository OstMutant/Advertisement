package org.ost.advertisement.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdvertisementPortImpl implements AdvertisementPort {

    private final AdvertisementService service;

    @Override
    public List<AdvertisementInfoDto> getFiltered(@NonNull AdvertisementFilterDto filter, int page, int size, @NonNull Sort sort, @NonNull Locale locale) {
        return service.getFiltered(filter, page, size, sort, locale);
    }

    @Override
    public int count(@NonNull AdvertisementFilterDto filter) {
        return service.count(filter);
    }

    @Override
    public Optional<AdvertisementInfoDto> findById(@NonNull Long id) {
        return service.findById(id);
    }

    @Override
    @Transactional
    public Long save(@NonNull AdvertisementSaveDto dto) {
        return service.save(dto);
    }

    @Override
    @Transactional
    public void delete(@NonNull Long id, @NonNull Long actingUserId, Long version) {
        service.delete(id, actingUserId, version);
    }

    @Override
    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return service.findExistingIds(ids);
    }

    @Override
    public List<AdvertisementInfoDto> findByCreator(@NonNull Long userId) {
        return service.findByCreator(userId);
    }
}
