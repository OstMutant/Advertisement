package org.ost.advertisement.repository.advertisement.filter;

import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.createdAtEnd;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.createdAtStart;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.title;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.updatedAtEnd;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.updatedAtStart;
import static org.ost.advertisement.repository.advertisement.mapping.AdvertisementProjection.CREATED_AT;
import static org.ost.advertisement.repository.advertisement.mapping.AdvertisementProjection.TITLE;
import static org.ost.advertisement.repository.advertisement.mapping.AdvertisementProjection.UPDATED_AT;
import static org.ost.advertisement.repository.query.filter.Condition.after;
import static org.ost.advertisement.repository.query.filter.Condition.before;
import static org.ost.advertisement.repository.query.filter.Condition.like;
import static org.ost.advertisement.repository.query.filter.SimpleFilterRelation.of;

import java.util.List;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.repository.query.filter.FilterApplier;

public class AdvertisementFilterApplier extends FilterApplier<AdvertisementFilterDto> {

	public AdvertisementFilterApplier() {
		relations.addAll(List.of(
			of(title, TITLE, (projection, value) -> like(projection, value.getTitle())),
			of(createdAtStart, CREATED_AT, (projection, value) -> after(projection, value.getCreatedAtStart())),
			of(createdAtEnd, CREATED_AT, (projection, value) -> before(projection, value.getCreatedAtEnd())),
			of(updatedAtStart, UPDATED_AT, (projection, value) -> after(projection, value.getUpdatedAtStart())),
			of(updatedAtEnd, UPDATED_AT, (projection, value) -> before(projection, value.getUpdatedAtEnd()))
		));
	}
}
