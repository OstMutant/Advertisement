package org.ost.advertisement.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entyties.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFilter {

	private String nameFilter;
	private Role role;
	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	private Long startId;
	private Long endId;

	public void clear() {
		this.nameFilter = null;
		this.role = null;
		this.startId = null;
		this.endId = null;
		this.createdAtStart = null;
		this.createdAtEnd = null;
		this.updatedAtStart = null;
		this.updatedAtEnd = null;
	}

	public void copyFrom(UserFilter other) {
		this.nameFilter = other.nameFilter;
		this.role = other.role;
		this.startId = other.startId;
		this.endId = other.endId;
		this.createdAtStart = other.createdAtStart;
		this.createdAtEnd = other.createdAtEnd;
		this.updatedAtStart = other.updatedAtStart;
		this.updatedAtEnd = other.updatedAtEnd;
	}

}
