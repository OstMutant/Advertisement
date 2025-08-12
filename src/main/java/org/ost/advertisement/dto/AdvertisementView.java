package org.ost.advertisement.dto;

import java.time.Instant;

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
