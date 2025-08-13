package org.ost.advertisement.dto;

import java.time.Instant;
import lombok.Builder;

@Builder
public record AdvertisementView(
	Long id,
	String title,
	String description,
	Instant createdAt,
	Instant updatedAt,
	Long userId,
	String userName,
	String userEmail
) {

}
