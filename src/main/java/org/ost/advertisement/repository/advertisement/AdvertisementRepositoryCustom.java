package org.ost.advertisement.repository.advertisement;

import java.util.List;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.springframework.data.domain.Pageable;

public interface AdvertisementRepositoryCustom {

	List<AdvertisementInfoDto> findByFilter(AdvertisementFilterDto filter, Pageable pageable);

	Long countByFilter(AdvertisementFilterDto filter);
}
