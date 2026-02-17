package org.ost.advertisement.services;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@AllArgsConstructor
public class ValidationService<T> {

    private final Validator validator;

    public boolean isValid(T obj) {
        return validator.validate(obj).isEmpty();
    }

    /**
     * Checks field-level constraints directly on the property.
     * Note: does NOT catch class-level (@ValidRange) violations.
     */
    public boolean isValidProperty(T obj, String property) {
        return validator.validateProperty(obj, property).isEmpty();
    }

    /**
     * Checks all violations (including class-level @ValidRange) and returns
     * true only if there are no violations whose property path matches the
     * given property name.
     */
    public boolean isValidForProperty(T obj, String property) {
        Set<ConstraintViolation<T>> violations = validator.validate(obj);
        return violations.stream()
                .noneMatch(v -> property.equals(v.getPropertyPath().toString()));
    }
}