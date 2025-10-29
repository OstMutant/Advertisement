package org.ost.advertisement.services;

import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ValidationService<T> {

	private final Validator validator;

	public boolean isValid(T obj) {
		return validator.validate(obj).isEmpty();
	}

	public boolean isValidProperty(T obj, String property) {
		return validator.validate(obj).stream()
			.noneMatch(v -> v.getPropertyPath().toString().contains(property));
	}

//	public boolean isValidProperty(T obj, String property) {
//		return validator.validateProperty(obj, property).isEmpty();
//	}

}
