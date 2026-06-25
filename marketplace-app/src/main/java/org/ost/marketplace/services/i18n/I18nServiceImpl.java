package org.ost.marketplace.services.i18n;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class I18nServiceImpl implements I18nService {

    private final MessageSource messageSource;
    private final LocaleProvider localeProvider;

    @Override
    public String get(@NonNull String key, Object... args) {
        return messageSource.getMessage(key, args, localeProvider.getCurrentLocale());
    }
}
