package org.ost.advertisement.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFilter {

	private String nameFilter;
	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	private Long startId;
	private Long endId;

	public void clear() {
		this.nameFilter = null;
		this.startId = null;
		this.endId = null;
		this.createdAtStart = null;
		this.createdAtEnd = null;
		this.updatedAtStart = null;
		this.updatedAtEnd = null;
	}

	public void copyFrom(UserFilter other) {
		this.setNameFilter(other.getNameFilter());
		this.setStartId(other.getStartId());
		this.setCreatedAtStart(other.getCreatedAtStart());
		this.setUpdatedAtStart(other.getUpdatedAtStart());
	}

}
