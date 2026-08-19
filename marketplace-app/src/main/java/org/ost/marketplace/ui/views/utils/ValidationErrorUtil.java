package org.ost.marketplace.ui.views.utils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationErrorUtil {

    public static String buildMessage(ConstraintViolationException ex) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .distinct()
                .sorted()
                .collect(Collectors.joining("\n"));
    }
}
