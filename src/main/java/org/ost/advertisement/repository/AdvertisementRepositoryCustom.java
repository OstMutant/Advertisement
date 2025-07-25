package org.ost.advertisement.repository;

import java.util.List;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.entyties.Advertisement;
import org.springframework.data.domain.Pageable;

public interface AdvertisementRepositoryCustom {

	List<Advertisement> findByFilter(AdvertisementFilter filter, Pageable pageable);

	Long countByFilter(AdvertisementFilter filter);
}
