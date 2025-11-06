package org.ost.advertisement.repository.advertisement.filter;

import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.createdAtEnd;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.createdAtStart;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.title;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.updatedAtEnd;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.updatedAtStart;
import static org.ost.advertisement.repository.advertisement.mapping.AdvertisementProjection.CREATED_AT;
import static org.ost.advertisement.repository.advertisement.mapping.AdvertisementProjection.TITLE;
import static org.ost.advertisement.repository.advertisement.mapping.AdvertisementProjection.UPDATED_AT;
import static org.ost.advertisement.repository.query.filter.SimpleFilterRelation.of;

import java.util.List;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.repository.query.filter.FilterApplier;

public class AdvertisementFilterApplier extends FilterApplier<AdvertisementFilterDto> {

	public AdvertisementFilterApplier() {
		relations.addAll(List.of(
			of(title, TITLE, (f, fc, r) -> r.like(f.getTitle(), fc)),
			of(createdAtStart, CREATED_AT, (f, fc, r) -> r.after(f.getCreatedAtStart(), fc)),
			of(createdAtEnd, CREATED_AT, (f, fc, r) -> r.before(f.getCreatedAtEnd(), fc)),
			of(updatedAtStart, UPDATED_AT, (f, fc, r) -> r.after(f.getUpdatedAtStart(), fc)),
			of(updatedAtEnd, UPDATED_AT, (f, fc, r) -> r.before(f.getUpdatedAtEnd(), fc))
		));
	}
}
