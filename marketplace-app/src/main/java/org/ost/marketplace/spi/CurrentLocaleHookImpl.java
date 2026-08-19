package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.orchestrator.spi.CurrentLocaleHook;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class CurrentLocaleHookImpl implements CurrentLocaleHook {

    private final LocaleProvider localeProvider;

    @Override
    public Locale getCurrentLocale() {
        return localeProvider.getCurrentLocale();
    }
}
