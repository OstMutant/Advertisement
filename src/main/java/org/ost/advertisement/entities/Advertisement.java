package org.ost.advertisement.entities;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ost.advertisement.security.UserIdMarker;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@Table("advertisement")
@Getter
@Setter
public class Advertisement implements UserIdMarker {

	@Id
	private Long id;

	private String title;
	private String description;

	private Instant createdAt;
	private Instant updatedAt;

	private Long userId;
}
