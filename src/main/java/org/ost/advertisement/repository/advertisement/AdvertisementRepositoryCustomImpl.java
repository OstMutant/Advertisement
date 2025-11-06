package org.ost.advertisement.repository.advertisement;

import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.repository.RepositoryCustom;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdvertisementRepositoryCustomImpl
	extends RepositoryCustom<AdvertisementInfoDto, AdvertisementFilterDto>
	implements AdvertisementRepositoryCustom {

	private static final AdvertisementProjection ADVERTISEMENT_PROJECTION = new AdvertisementProjection();
	private static final AdvertisementFilterBuilder ADVERTISEMENT_FILTER_BUILDER = new AdvertisementFilterBuilder();

	public AdvertisementRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, ADVERTISEMENT_PROJECTION, ADVERTISEMENT_FILTER_BUILDER);
	}
}
