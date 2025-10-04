package org.ost.advertisement.entities;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ost.advertisement.security.UserIdMarker;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Table("advertisement")
public class Advertisement implements UserIdMarker {

	public Advertisement(Long userId) {
		this.userId = userId;
	}

	@Id
	private Long id;

	private String title;
	private String description;

	@CreatedDate
	private Instant createdAt;
	@LastModifiedDate
	private Instant updatedAt;

	private Long userId;
}
