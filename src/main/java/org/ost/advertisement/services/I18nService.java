package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.constants.I18nKey;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class I18nService {

    private final MessageSource messageSource;
    private final SessionService sessionService;

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, sessionService.getCurrentLocale());
    }

    public String get(I18nKey key, Object... args) {
        return messageSource.getMessage(key.key(), args, sessionService.getCurrentLocale());
    }
}
