package org.ost.advertisement.services;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@NoArgsConstructor
public class ValidationService<T> {

	private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	public boolean isValid(T obj) {
		return validator.validate(obj).isEmpty();
	}

	public boolean isValidProperty(T obj, String property) {
		return validator.validateProperty(obj, property).isEmpty();
	}

	public boolean hasViolationFor(T obj, String property) {
		return hasViolationFor(getViolationFor(obj), property);
	}

	public Set<ConstraintViolation<T>> getViolationFor(T obj) {
		return validator.validate(obj);
	}

	public boolean hasViolationFor(Set<ConstraintViolation<T>> violations, String property) {
		return violations.stream()
			.anyMatch(v -> v.getPropertyPath().toString().contains(property));
	}
}
