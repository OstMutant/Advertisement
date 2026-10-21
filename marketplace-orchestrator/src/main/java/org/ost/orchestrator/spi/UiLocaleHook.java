package org.ost.orchestrator.spi;

import java.util.Locale;
import java.util.Optional;

/**
 * Hook: marketplace-orchestrator -> marketplace-app.
 * Answers the language of the Vaadin UI session when the calling thread is a UI thread and is empty
 * everywhere else (the REST API). Same forwarder shape as {@code UiLabelHook}/{@code SessionActorHook}.
 */
@FunctionalInterface
public interface UiLocaleHook {
    Optional<Locale> currentUiLocale();
}
