package org.ost.advertisement.validation.filter;

import static org.ost.advertisement.validation.ValidationRules.isNotValidDateRange;
import static org.ost.advertisement.validation.ValidationRules.isNotValidNumberRange;

import java.time.Instant;
import org.ost.advertisement.validation.ValidationRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractFilterValidator<T> {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	public abstract boolean validate(T filter);

	protected boolean validateText(String label, String value, int maxLength) {
		if (ValidationRules.isTooLong(value, maxLength)) {
			log.warn("{} too long: {} chars", label, value.length());
			return false;
		}
		return true;
	}

	protected boolean validateDateRange(String label, Instant start, Instant end) {
		if (isNotValidDateRange(start, end)) {
			log.warn("Invalid {} range: from={} after to={}", label, start, end);
			return false;
		}
		return true;
	}

	protected boolean validateNumberRange(String label, Long min, Long max) {
		if (isNotValidNumberRange(min, max)) {
			log.warn("Invalid {} range: from={} after to={}", label, min, max);
			return false;
		}
		return true;
	}
}

