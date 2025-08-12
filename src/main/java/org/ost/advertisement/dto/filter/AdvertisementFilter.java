package org.ost.advertisement.dto.filter;

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

	private String title;

	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	@Override
	public void clear() {
		this.title = null;

		this.createdAtStart = null;
		this.createdAtEnd = null;
		this.updatedAtStart = null;
		this.updatedAtEnd = null;
	}

	@Override
	public void copyFrom(AdvertisementFilter other) {
		this.title = other.title;
		this.createdAtStart = other.createdAtStart;
		this.createdAtEnd = other.createdAtEnd;
		this.updatedAtStart = other.updatedAtStart;
		this.updatedAtEnd = other.updatedAtEnd;
	}

	@Override
	public AdvertisementFilter copy() {
		AdvertisementFilter filter = new AdvertisementFilter();
		filter.title = this.title;

		filter.createdAtStart = this.createdAtStart;
		filter.createdAtEnd = this.createdAtEnd;
		filter.updatedAtStart = this.updatedAtStart;
		filter.updatedAtEnd = this.updatedAtEnd;

		return filter;
	}

	@Override
	public boolean isValid() {
		return isValidDateRange(getCreatedAtStart(), getCreatedAtEnd())
			&& isValidDateRange(getUpdatedAtStart(), getUpdatedAtEnd());
	}
}
