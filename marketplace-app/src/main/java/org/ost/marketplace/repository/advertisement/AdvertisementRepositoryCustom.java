package org.ost.marketplace.repository.advertisement;

import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface AdvertisementRepositoryCustom {

    List<AdvertisementInfoDto> findByFilter(AdvertisementFilterDto filter, Pageable pageable);

    Long countByFilter(AdvertisementFilterDto filter);

    void softDelete(Long id, Long deletedByUserId);

    Optional<AdvertisementInfoDto> findAdvertisementById(Long id);

    int deleteOlderThan(int days);
}
