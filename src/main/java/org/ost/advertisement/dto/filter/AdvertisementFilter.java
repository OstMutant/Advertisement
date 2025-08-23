package org.ost.advertisement.dto.filter;

import static org.ost.advertisement.dto.filter.utils.FilterUtil.isValidDateRange;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class AdvertisementFilter implements FilterValidation<AdvertisementFilter> {

	private String title;

	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	public static AdvertisementFilter empty() {
		return new AdvertisementFilter();
	}

	@Override
	public boolean isValid() {
		return isValidDateRange(createdAtStart, createdAtEnd)
			&& isValidDateRange(updatedAtStart, updatedAtEnd);
	}
}
