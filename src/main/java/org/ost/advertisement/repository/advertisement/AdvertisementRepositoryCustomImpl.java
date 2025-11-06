package org.ost.advertisement.repository.advertisement;

import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.repository.RepositoryCustom;
import org.ost.advertisement.repository.advertisement.filter.AdvertisementFilterApplier;
import org.ost.advertisement.repository.advertisement.mapping.AdvertisementProjection;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdvertisementRepositoryCustomImpl
	extends RepositoryCustom<AdvertisementInfoDto, AdvertisementFilterDto>
	implements AdvertisementRepositoryCustom {

	private static final AdvertisementProjection ADVERTISEMENT_MAPPER = new AdvertisementProjection();
	private static final AdvertisementFilterApplier ADVERTISEMENT_FILTER_APPLIER = new AdvertisementFilterApplier();

	public AdvertisementRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, ADVERTISEMENT_MAPPER, ADVERTISEMENT_FILTER_APPLIER);
	}
}
