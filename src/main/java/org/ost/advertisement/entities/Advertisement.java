package org.ost.advertisement.entities;

import java.time.Instant;
import lombok.Getter;
import org.ost.advertisement.security.UserIdMarker;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Table("advertisement")
public record Advertisement(
	@Id @Getter Long id,
	String title,
	String description,
	@CreatedDate Instant createdAt,
	@LastModifiedDate Instant updatedAt,
	@CreatedBy Long createdByUserId,
	@LastModifiedBy Long lastModifiedByUserId
) implements EntityMarker {

	@Override
	public Long getOwnerUserId() {
		return createdByUserId;
	}
}
