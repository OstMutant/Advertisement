package org.ost.advertisement.repository.advertisement;

import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.createdAtEnd;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.createdAtStart;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.title;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.updatedAtEnd;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.updatedAtStart;
import static org.ost.advertisement.repository.advertisement.AdvertisementProjection.CREATED_AT;
import static org.ost.advertisement.repository.advertisement.AdvertisementProjection.TITLE;
import static org.ost.advertisement.repository.advertisement.AdvertisementProjection.UPDATED_AT;
import static org.ost.advertisement.repository.query.filter.SqlCondition.after;
import static org.ost.advertisement.repository.query.filter.SqlCondition.before;
import static org.ost.advertisement.repository.query.filter.SqlCondition.like;
import static org.ost.advertisement.repository.query.filter.DefaultFilterBinding.of;

import java.util.List;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.repository.query.filter.FilterBuilder;

public class AdvertisementFilterBuilder extends FilterBuilder<AdvertisementFilterDto> {

	public AdvertisementFilterBuilder() {
		relations.addAll(List.of(
			of(title, TITLE, (mapping, value) -> like(mapping, value.getTitle())),
			of(createdAtStart, CREATED_AT, (mapping, value) -> after(mapping, value.getCreatedAtStart())),
			of(createdAtEnd, CREATED_AT, (mapping, value) -> before(mapping, value.getCreatedAtEnd())),
			of(updatedAtStart, UPDATED_AT, (mapping, value) -> after(mapping, value.getUpdatedAtStart())),
			of(updatedAtEnd, UPDATED_AT, (mapping, value) -> before(mapping, value.getUpdatedAtEnd()))
		));
	}
}
