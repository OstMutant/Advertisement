package org.ost.advertisement.repository.advertisement;

import java.util.List;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.springframework.data.domain.Pageable;

public interface AdvertisementRepositoryCustom {

	List<AdvertisementView> findByFilter(AdvertisementFilter filter, Pageable pageable);

	Long countByFilter(AdvertisementFilter filter);
}
