package org.ost.advertisement.entyties;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@Table("advertisement")
@Getter
@Setter
public class Advertisement {

	@Id
	private Long id;

	private String title;
	private String description;
	private String category;
	private String location;
	private String contactInfo;

	private String imageUrls;
	private String status;

	private Instant createdAt;
	private Instant updatedAt;

	private Long userId;
}
