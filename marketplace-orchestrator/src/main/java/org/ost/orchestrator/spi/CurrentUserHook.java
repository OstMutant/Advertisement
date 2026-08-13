package org.ost.orchestrator.spi;

import org.ost.platform.user.dto.UserDto;

import java.util.Optional;

/**
 * Hook: marketplace-orchestrator -> marketplace-app.
 * Resolves the current request's authenticated user and their stored locale preference. Each
 * adapter (Vaadin's session-backed Spring Security context today, a future REST adapter's own
 * auth mechanism) implements this its own way -- marketplace-orchestrator never assumes how
 * identity is resolved, only that some adapter can answer this. Same forwarder shape as
 * {@code SessionActorHook}/{@code CurrentLocaleHook} -- lives here, not platform-commons, for the
 * same mandatory-dependency reasoning.
 */
public interface CurrentUserHook {
    Optional<UserDto> getCurrentUser();

    Optional<String> getCurrentUserLocale();
}
