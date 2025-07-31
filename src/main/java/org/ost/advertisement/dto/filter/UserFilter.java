package org.ost.advertisement.dto.filter;

import static org.ost.advertisement.utils.FilterUtil.isValidDateRange;
import static org.ost.advertisement.utils.FilterUtil.isValidNumberRange;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entyties.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserFilter implements Filter<UserFilter> {

	private String name;
	private String email;
	private Role role;
	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	private Long startId;
	private Long endId;

	@Override
	public void clear() {
		this.name = null;
		this.email = null;
		this.role = null;
		this.startId = null;
		this.endId = null;
		this.createdAtStart = null;
		this.createdAtEnd = null;
		this.updatedAtStart = null;
		this.updatedAtEnd = null;
	}

	@Override
	public void copyFrom(UserFilter other) {
		this.name = other.name;
		this.email = other.email;
		this.role = other.role;
		this.startId = other.startId;
		this.endId = other.endId;
		this.createdAtStart = other.createdAtStart;
		this.createdAtEnd = other.createdAtEnd;
		this.updatedAtStart = other.updatedAtStart;
		this.updatedAtEnd = other.updatedAtEnd;
	}

	@Override
	public UserFilter copy() {
		UserFilter filter = new UserFilter();
		filter.name = this.name;
		filter.email = this.email;
		filter.role = this.role;
		filter.startId = this.startId;
		filter.endId = this.endId;
		filter.createdAtStart = this.createdAtStart;
		filter.createdAtEnd = this.createdAtEnd;
		filter.updatedAtStart = this.updatedAtStart;
		filter.updatedAtEnd = this.updatedAtEnd;
		return filter;
	}

	@Override
	public boolean isValid() {
		return isValidNumberRange(getStartId(), getEndId())
			&& isValidDateRange(getCreatedAtStart(), getCreatedAtEnd())
			&& isValidDateRange(getUpdatedAtStart(), getUpdatedAtEnd());
	}

}
