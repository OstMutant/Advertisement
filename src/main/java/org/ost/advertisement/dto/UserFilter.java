package org.ost.advertisement.dto;

import java.time.Instant;
import java.util.Objects;
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
		this.nameFilter = other.nameFilter;
		this.startId = other.startId;
		this.endId = other.endId;
		this.createdAtStart = other.createdAtStart;
		this.createdAtEnd = other.createdAtEnd;
		this.updatedAtStart = other.updatedAtStart;
		this.updatedAtEnd = other.updatedAtEnd;
	}

	public boolean isEqualTo(UserFilter other) {
		return Objects.equals(nameFilter, other.nameFilter)
			&& Objects.equals(startId, other.startId)
			&& Objects.equals(endId, other.endId)
			&& Objects.equals(createdAtStart, other.createdAtStart)
			&& Objects.equals(createdAtEnd, other.createdAtEnd)
			&& Objects.equals(updatedAtStart, other.updatedAtStart)
			&& Objects.equals(updatedAtEnd, other.updatedAtEnd);
	}

}
