package org.ost.advertisement.dto.filter;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.validation.ValidRange;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ValidRange.List({
	@ValidRange(start = "createdAtStart", end = "createdAtEnd", message = "createdAtStart must not be after createdAtEnd"),
	@ValidRange(start = "updatedAtStart", end = "updatedAtEnd", message = "updatedAtStart must not be after updatedAtEnd"),
	@ValidRange(start = "startId", end = "endId", message = "startId must not be greater than endId")
})
public class UserFilterDto {

	@Size(max = 255, message = "Name must not exceed 255 characters")
	private String name;
	@Size(max = 255, message = "Name must not exceed 255 characters")
	private String email;
	private Role role;
	private Instant createdAtStart;
	private Instant createdAtEnd;
	private Instant updatedAtStart;
	private Instant updatedAtEnd;

	private Long startId;
	private Long endId;

	public static UserFilterDto empty() {
		return new UserFilterDto();
	}

}
