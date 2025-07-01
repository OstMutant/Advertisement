package org.ost.advertisement.entyties; // Note: 'entyties' as per your existing structure

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@Table("advertisement") // Renamed table name from "ad_listing" to "advertisement"
@Getter
@Setter
public class Advertisement {

	@Id
	private Long id;

	private String title;
	private String description;
	private String category; // Stored as a simple string for now
	private String location; // Stored as a simple string for now
	private String contactInfo;

	@Column("image_urls")
	private String imageUrls; // e.g., "url1,url2,url3" or a JSON string

	private String status; // e.g., "ACTIVE", "EXPIRED", "DRAFT"

	@Column("created_at")
	private Instant createdAt;

	@Column("updated_at")
	private Instant updatedAt;

	@Column("user_id")
	private Long userId; // Foreign key to the User who created the ad
}
