package org.ost.advertisement.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import org.ost.advertisement.security.UserIdMarker;

@Builder
public record AdvertisementView(
	Long id,
	String title,
	String description,
	Instant createdAt,
	Instant updatedAt,
	@Getter Long userId,
	String userName,
	String userEmail
) implements UserIdMarker {

}
