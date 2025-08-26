package org.ost.advertisement.dto.filter;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ost.advertisement.validation.ValidRange;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ValidRange.List({
	@ValidRange(start = "createdAtStart", end = "createdAtEnd", message = "createdAtStart must not be after createdAtEnd"),
	@ValidRange(start = "updatedAtStart", end = "updatedAtEnd", message = "updatedAtStart must not be after updatedAtEnd")
})
public class AdvertisementFilter {

	@Size(max = 255, message = "Name must not exceed 255 characters")
	private String title;

	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	public static AdvertisementFilter empty() {
		return new AdvertisementFilter();
	}
}
