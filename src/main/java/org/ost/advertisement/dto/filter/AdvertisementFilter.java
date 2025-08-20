package org.ost.advertisement.dto.filter;

import static org.ost.advertisement.utils.FilterUtil.isValidDateRange;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdvertisementFilter implements Filter<AdvertisementFilter> {

	private String title;

	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	public static AdvertisementFilter empty() {
		return new AdvertisementFilter();
	}

	@Override
	public void clear() {
		copyFrom(empty());
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
		return AdvertisementFilter.builder()
			.title(this.getTitle())
			.createdAtStart(this.getCreatedAtStart())
			.createdAtEnd(this.getCreatedAtEnd())
			.updatedAtStart(this.getUpdatedAtStart())
			.updatedAtEnd(this.getUpdatedAtEnd())
			.build();
	}

	@Override
	public boolean isValid() {
		return isValidDateRange(createdAtStart, createdAtEnd)
			&& isValidDateRange(updatedAtStart, updatedAtEnd);
	}
}
