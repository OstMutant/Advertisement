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

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdvertisementPortImpl implements AdvertisementPort {

    private final AdvertisementService service;

    @Override
    public List<AdvertisementInfoDto> getFiltered(@NonNull AdvertisementFilterDto filter, int page, int size, @NonNull Sort sort) {
        return service.getFiltered(filter, page, size, sort);
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
    public Long save(@NonNull AdvertisementSaveDto dto, @NonNull Long actingUserId) {
        return service.save(dto, actingUserId);
    }

    @Override
    public void delete(@NonNull Long id, @NonNull Long actingUserId) {
        service.delete(id, actingUserId);
    }

    @Override
    public void onMediaChanged(@NonNull Long entityId) {
        service.onMediaChanged(entityId);
    }

    @Override
    public List<Long> findExistingIds(@NonNull Long[] ids) {
        return service.findExistingIds(ids);
    }
}
