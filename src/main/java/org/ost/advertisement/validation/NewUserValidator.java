package org.ost.advertisement.validation;

import static org.ost.advertisement.Constants.EMAIL_PATTERN;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NewUserValidator {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	public boolean validateName(String value) {
		String label = "Name";
		if (value == null || value.isBlank()) {
			log.warn("{} is null or blank", label);
			return false;
		}
		if (ValidationRules.isTooLong(value, 255)) {
			log.warn("{} too long: {} chars", label, value.length());
			return false;
		}
		return true;
	}

	public boolean validateEmail(String value) {
		String label = "Email";
		if (!EMAIL_PATTERN.matcher(value).matches()) {
			log.warn("{} is not match email pattern", label);
			return false;
		}
		if (ValidationRules.isTooLong(value, 255)) {
			log.warn("{} too long: {} chars", label, value.length());
			return false;
		}
		return true;
	}

	public boolean validatePassword(String value) {
		String label = "Password";
		if (value == null || value.length() < 6) {
			log.warn("{} is null or <6 chars", label);
			return false;
		}
		if (ValidationRules.isTooLong(value, 255)) {
			log.warn("{} too long: {} chars", label, value.length());
			return false;
		}
		return true;
	}
}
