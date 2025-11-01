package org.ost.advertisement.entities;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Value
@Builder
@FieldNameConstants
@Table("advertisement")
public class Advertisement implements EntityMarker {

	@Id
	Long id;
	String title;
	String description;

	@CreatedDate
	Instant createdAt;

	@LastModifiedDate
	Instant updatedAt;

	@CreatedBy
	Long createdByUserId;

	@LastModifiedBy
	Long lastModifiedByUserId;

	@Override
	public Long getOwnerUserId() {
		return createdByUserId;
	}
}
