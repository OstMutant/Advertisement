package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.common.I18nKey;
import org.ost.advertisement.services.auth.LocaleProvider;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class I18nService {

    private final MessageSource messageSource;
    private final LocaleProvider localeProvider;

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, localeProvider.getCurrentLocale());
    }

    public String get(I18nKey key, Object... args) {
        return messageSource.getMessage(key.key(), args, localeProvider.getCurrentLocale());
    }
}
