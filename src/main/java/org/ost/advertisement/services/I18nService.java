package org.ost.advertisement.services;

import org.ost.advertisement.ui.utils.SessionUtil;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class I18nService {

	private final MessageSource messageSource;

	public I18nService(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	public String get(String key, Object... args) {
		return messageSource.getMessage(key, args, SessionUtil.getCurrentLocale());
	}
}
