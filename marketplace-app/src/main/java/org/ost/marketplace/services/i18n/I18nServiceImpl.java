package org.ost.marketplace.services.i18n;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.CurrentLocaleService;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/** Translates a key through {@code MessageSource} in the language {@code CurrentLocaleService} resolves for the caller, UI or REST. */
@Service
@RequiredArgsConstructor
public class I18nServiceImpl implements I18nService {

    private final MessageSource messageSource;
    private final CurrentLocaleService currentLocaleService;

    @Override
    public String get(@NonNull String key, Object... args) {
        return messageSource.getMessage(key, args, currentLocaleService.getCurrentLocale());
    }
}
