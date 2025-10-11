package org.ost.advertisement.ui.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class AdvertisementEditDto {

	private Long id;

	private String title;
	private String description;

	private Instant createdAt;
	private Instant updatedAt;

	private Long createdByUserId;
	private Long lastModifiedByUserId;
}
