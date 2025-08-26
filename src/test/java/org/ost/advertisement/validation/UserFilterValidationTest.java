package org.ost.advertisement.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ost.advertisement.dto.filter.UserFilter;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserFilterValidationTest {

	private static Validator validator;

	@BeforeAll
	static void setupValidator() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@Test
	void validFilter_shouldPass() {
		UserFilter filter = UserFilter.builder()
			.name("Ostap")
			.email("ostap@example.com")
			.createdAtStart(Instant.parse("2023-01-01T00:00:00Z"))
			.createdAtEnd(Instant.parse("2023-12-31T00:00:00Z"))
			.updatedAtStart(Instant.parse("2023-01-01T00:00:00Z"))
			.updatedAtEnd(Instant.parse("2023-12-31T00:00:00Z"))
			.build();

		Set<ConstraintViolation<UserFilter>> violations = validator.validate(filter);
		assertThat(violations).isEmpty();
	}

	@Test
	void nameTooLong_shouldFail() {
		String longName = "x".repeat(300);
		UserFilter filter = UserFilter.builder().name(longName).build();

		Set<ConstraintViolation<UserFilter>> violations = validator.validate(filter);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
	}

//	@Test
//	void invalidCreatedDateRange_shouldFail() {
//		UserFilter filter = UserFilter.builder()
//			.createdAtStart(Instant.parse("2024-01-01T00:00:00Z"))
//			.createdAtEnd(Instant.parse("2023-01-01T00:00:00Z"))
//			.build();
//
//		Set<ConstraintViolation<UserFilter>> violations = validator.validate(filter);
//		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("createdAtStart"));
//	}

//	@Test
//	void invalidUpdatedDateRange_shouldFail() {
//		UserFilter filter = UserFilter.builder()
//			.updatedAtStart(Instant.parse("2024-01-01T00:00:00Z"))
//			.updatedAtEnd(Instant.parse("2023-01-01T00:00:00Z"))
//			.build();
//
//		Set<ConstraintViolation<UserFilter>> violations = validator.validate(filter);
//		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("updatedAtStart"));
//	}
}
