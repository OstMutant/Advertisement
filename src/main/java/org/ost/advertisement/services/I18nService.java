package org.ost.advertisement.services;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class I18nService {

	private final MessageSource messageSource;

	public I18nService(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	public String get(String key, Locale locale, Object... args) {
		return messageSource.getMessage(key, args, locale);
	}
}
