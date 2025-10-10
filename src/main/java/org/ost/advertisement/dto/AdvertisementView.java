package org.ost.advertisement.dto;

import java.time.Instant;
import lombok.Builder;
import org.ost.advertisement.security.UserIdMarker;

@Builder
public record AdvertisementView(
	Long id,
	String title,
	String description,
	Instant createdAt,
	Instant updatedAt,
	Long createdByUserId,
	String createdByUserName,
	String createdByUserEmail
) implements UserIdMarker {

	@Override
	public Long getOwnerUserId() {
		return createdByUserId;
	}
}
