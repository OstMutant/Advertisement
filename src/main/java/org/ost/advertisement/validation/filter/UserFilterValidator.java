package org.ost.advertisement.validation.filter;

import java.time.Instant;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserFilterValidator extends AbstractFilterValidator<UserFilter> {

	private static final Logger log = LoggerFactory.getLogger(UserFilterValidator.class);

	@Override
	public boolean validate(UserFilter filter) {
		return validateRole(filter.getRole())
			&& validateIdRange(filter.getStartId(), filter.getEndId())
			&& validateCreatedDateRange(filter.getCreatedAtStart(), filter.getCreatedAtEnd())
			&& validateUpdatedDateRange(filter.getUpdatedAtStart(), filter.getUpdatedAtEnd())
			&& validateName(filter.getName())
			&& validateEmail(filter.getEmail());
	}

	public boolean validateIdRange(Long start, Long end) {
		return validateNumberRange("id", start, end);
	}

	public boolean validateCreatedDateRange(Instant start, Instant end) {
		return validateDateRange("createdAt", start, end);
	}

	public boolean validateUpdatedDateRange(Instant start, Instant end) {
		return validateDateRange("updatedAt", start, end);
	}

	public boolean validateName(String name) {
		return validateText("Name", name, 255);
	}

	public boolean validateEmail(String email) {
		return validateText("Email", email, 255);
	}

	public boolean validateRole(Role role) {
		if (role == null) {
			log.debug("Role not specified — skipping role validation");
		}
		return true;
	}
}
