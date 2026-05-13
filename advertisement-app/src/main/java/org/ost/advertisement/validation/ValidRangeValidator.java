package org.ost.advertisement.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public class ValidRangeValidator implements ConstraintValidator<ValidRange, Object> {

    private static final Logger log = LoggerFactory.getLogger(ValidRangeValidator.class);

    private record FieldKey(Class<?> type, String start, String end) {}

    private static final ConcurrentHashMap<FieldKey, Field[]> CACHE = new ConcurrentHashMap<>();

    private String startFieldName;
    private String endFieldName;

    @Override
    public void initialize(ValidRange constraintAnnotation) {
        this.startFieldName = constraintAnnotation.start();
        this.endFieldName = constraintAnnotation.end();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        try {
            Field[] fields = CACHE.computeIfAbsent(
                new FieldKey(value.getClass(), startFieldName, endFieldName),
                k -> {
                    try {
                        Field s = k.type().getDeclaredField(k.start());
                        Field e = k.type().getDeclaredField(k.end());
                        s.setAccessible(true);
                        e.setAccessible(true);
                        return new Field[]{s, e};
                    } catch (NoSuchFieldException ex) {
                        throw new IllegalArgumentException(
                            "Field not found: " + k.start() + " or " + k.end(), ex);
                    }
                }
            );

            Object start = fields[0].get(value);
            Object end   = fields[1].get(value);

            if (start == null || end == null) return true;

            if (!(start instanceof Comparable) || !(end instanceof Comparable)) {
                log.warn("Fields {} and {} are not Comparable", startFieldName, endFieldName);
                return true;
            }

            @SuppressWarnings("unchecked")
            Comparable<Object> startComparable = (Comparable<Object>) start;
            @SuppressWarnings("unchecked")
            Comparable<Object> endComparable = (Comparable<Object>) end;

            if (startComparable.compareTo(endComparable) <= 0) return true;

            context.disableDefaultConstraintViolation();
            String msg = startFieldName + " must not be after " + endFieldName;
            context.buildConstraintViolationWithTemplate(msg).addPropertyNode(startFieldName).addConstraintViolation();
            context.buildConstraintViolationWithTemplate(msg).addPropertyNode(endFieldName).addConstraintViolation();

            return false;

        } catch (Exception e) {
            log.error("Error validating range between {} and {}: {}", startFieldName, endFieldName, e.getMessage(), e);
            return false;
        }
    }
}
