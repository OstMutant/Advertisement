package org.ost.orchestrator.spi;

import java.util.Locale;

/**
 * Hook: marketplace-orchestrator -> marketplace-app.
 * Orchestrator-owned Hook implementations call this to obtain the current request's locale.
 * Marketplace-app implements it against its own Vaadin-session-backed LocaleProvider. Same
 * forwarder shape as {@code SessionActorHook}/{@code UiLabelHook} -- lives here, not
 * platform-commons, for the same mandatory-dependency reasoning.
 */
@FunctionalInterface
public interface CurrentLocaleHook {
    Locale getCurrentLocale();
}
