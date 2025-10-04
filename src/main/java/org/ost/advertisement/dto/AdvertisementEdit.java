package org.ost.advertisement.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ost.advertisement.security.UserIdMarker;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class AdvertisementEdit implements UserIdMarker {

	private Long id;

	private String title;
	private String description;

	private Instant createdAt;
	private Instant updatedAt;

	private Long userId;
}
