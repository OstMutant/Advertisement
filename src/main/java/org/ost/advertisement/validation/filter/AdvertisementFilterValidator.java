package org.ost.advertisement.validation.filter;

import java.time.Instant;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdvertisementFilterValidator extends AbstractFilterValidator<AdvertisementFilter> {

	private static final Logger log = LoggerFactory.getLogger(AdvertisementFilterValidator.class);

	@Override
	public boolean validate(AdvertisementFilter filter) {
		return validateTitle(filter.getTitle())
			&& validateCreatedDateRange(filter.getCreatedAtStart(), filter.getCreatedAtEnd())
			&& validateUpdatedDateRange(filter.getUpdatedAtStart(), filter.getUpdatedAtEnd());
	}

	public boolean validateTitle(String title) {
		return validateText("Title", title, 255);
	}

	public boolean validateCreatedDateRange(Instant start, Instant end) {
		return validateDateRange("createdAt", start, end);
	}

	public boolean validateUpdatedDateRange(Instant start, Instant end) {
		return validateDateRange("updatedAt", start, end);
	}
}
