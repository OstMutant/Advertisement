package org.ost.advertisement.dto.filter;

import static org.ost.advertisement.utils.FilterUtil.isValidDateRange;
import static org.ost.advertisement.utils.FilterUtil.isValidNumberRange;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entities.Role;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class UserFilter implements FilterValidation<UserFilter> {

	private String name;
	private String email;
	private Role role;
	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	private Long startId;
	private Long endId;

	public static UserFilter empty() {
		return new UserFilter();
	}

	@Override
	public boolean isValid() {
		return isValidNumberRange(startId, endId)
			&& isValidDateRange(createdAtStart, createdAtEnd)
			&& isValidDateRange(updatedAtStart, updatedAtEnd);
	}

}
