package org.ost.advertisement.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class ValidRangeValidator implements ConstraintValidator<ValidRange, Object> {

	private static final Logger log = LoggerFactory.getLogger(ValidRangeValidator.class);

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
			Field startField = value.getClass().getDeclaredField(startFieldName);
			Field endField = value.getClass().getDeclaredField(endFieldName);
			startField.setAccessible(true);
			endField.setAccessible(true);

			Object start = startField.get(value);
			Object end = endField.get(value);

			if (start == null || end == null) {
				return true;
			}

			if (!(start instanceof Comparable) || !(end instanceof Comparable)) {
				log.warn("Fields {} and {} are not Comparable", startFieldName, endFieldName);
				return true;
			}

			Comparable startComparable = (Comparable) start;
			Comparable endComparable = (Comparable) end;

			if (startComparable.compareTo(endComparable) <= 0) {
				return true;
			}

			context.disableDefaultConstraintViolation();

			context.buildConstraintViolationWithTemplate(
					startFieldName + " must not be after " + endFieldName)
				.addPropertyNode(startFieldName)
				.addConstraintViolation();

			context.buildConstraintViolationWithTemplate(
					startFieldName + " must not be after " + endFieldName)
				.addPropertyNode(endFieldName)
				.addConstraintViolation();

			return false;

		} catch (Exception e) {
			log.error("Error validating range between {} and {}: {}", startFieldName, endFieldName, e.getMessage(), e);
			return false;
		}
	}
}
