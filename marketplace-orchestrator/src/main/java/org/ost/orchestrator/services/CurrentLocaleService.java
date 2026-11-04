package org.ost.orchestrator.services;

import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.spi.UiLocaleHook;
import org.ost.platform.core.model.SupportedLocales;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/** The language for the current caller on either channel: the UI session's own choice, else the user's saved language, else the request's {@code Accept-Language}, else English. */
@Service
@RequiredArgsConstructor
public class CurrentLocaleService {

    private final UiLocaleHook uiLocaleHook;
    private final CurrentUserService currentUserService;

    public Locale getCurrentLocale() {
        return uiLocaleHook.currentUiLocale()
                .or(this::savedLocale)
                .orElseGet(CurrentLocaleService::requestLocaleOrDefault);
    }

    private Optional<Locale> savedLocale() {
        return currentUserService.getCurrentUserLocale()
                .filter(tag -> !tag.isBlank())
                .map(Locale::forLanguageTag)
                .filter(SupportedLocales::isSupported);
    }

    private static Locale requestLocaleOrDefault() {
        Locale requested = LocaleContextHolder.getLocale();
        return SupportedLocales.isSupported(requested) ? requested : SupportedLocales.DEFAULT;
    }
}
