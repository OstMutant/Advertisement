package org.ost.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.LocaleProvider;
import org.ost.platform.core.i18n.TranslationKey;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class I18nServiceImpl implements I18nService {

    private final MessageSource messageSource;
    private final LocaleProvider localeProvider;

    @Override
    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, localeProvider.getCurrentLocale());
    }

    @Override
    public String get(TranslationKey key, Object... args) {
        return messageSource.getMessage(key.key(), args, localeProvider.getCurrentLocale());
    }
}
