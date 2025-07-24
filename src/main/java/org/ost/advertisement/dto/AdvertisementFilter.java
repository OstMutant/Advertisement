package org.ost.advertisement.dto;

import static org.ost.advertisement.utils.FilterUtil.isValidDateRange;
import static org.ost.advertisement.utils.FilterUtil.isValidNumberRange;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AdvertisementFilter implements Filter<AdvertisementFilter> {

	private String titleFilter;
	private String categoryFilter;
	private String locationFilter;
	private String statusFilter;

	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	private Long startId;
	private Long endId;

	@Override
	public void clear() {
		this.titleFilter = null;
		this.categoryFilter = null;
		this.locationFilter = null;
		this.statusFilter = null;

		this.createdAtStart = null;
		this.createdAtEnd = null;
		this.updatedAtStart = null;
		this.updatedAtEnd = null;

		this.startId = null;
		this.endId = null;
	}

	@Override
	public void copyFrom(AdvertisementFilter other) {
		this.titleFilter = other.titleFilter;
		this.categoryFilter = other.categoryFilter;
		this.locationFilter = other.locationFilter;
		this.statusFilter = other.statusFilter;

		this.createdAtStart = other.createdAtStart;
		this.createdAtEnd = other.createdAtEnd;
		this.updatedAtStart = other.updatedAtStart;
		this.updatedAtEnd = other.updatedAtEnd;

		this.startId = other.startId;
		this.endId = other.endId;
	}

	@Override
	public AdvertisementFilter copy() {
		AdvertisementFilter filter = new AdvertisementFilter();
		filter.titleFilter = this.titleFilter;
		filter.categoryFilter = this.categoryFilter;
		filter.locationFilter = this.locationFilter;
		filter.statusFilter = this.statusFilter;

		filter.createdAtStart = this.createdAtStart;
		filter.createdAtEnd = this.createdAtEnd;
		filter.updatedAtStart = this.updatedAtStart;
		filter.updatedAtEnd = this.updatedAtEnd;

		filter.startId = this.startId;
		filter.endId = this.endId;
		return filter;
	}

	@Override
	public boolean isValid() {
		return isValidNumberRange(getStartId(), getEndId())
			&& isValidDateRange(getCreatedAtStart(), getCreatedAtEnd())
			&& isValidDateRange(getUpdatedAtStart(), getUpdatedAtEnd());
	}
}
