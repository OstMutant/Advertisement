package org.ost.advertisement.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import org.ost.advertisement.entities.EntityMarker;

@Builder
public record AdvertisementInfoDto(
	@Getter Long id,
	String title,
	String description,
	Instant createdAt,
	Instant updatedAt,
	Long createdByUserId,
	String createdByUserName,
	String createdByUserEmail
) implements EntityMarker {

	@Override
	public Long getOwnerUserId() {
		return createdByUserId;
	}
}
