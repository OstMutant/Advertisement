package org.ost.orchestrator.spi;

import java.util.Optional;

/**
 * Hook: marketplace-orchestrator -> marketplace-app.
 * Orchestrator-owned Hook implementations call this to obtain the ID of the actor performing the
 * current request. Marketplace-app implements it against its own Spring Security session context.
 * Same shape as {@code CurrentActorHook} one layer up (platform-commons) -- a deliberate
 * forwarder, not a redesign. Lives here, not platform-commons, for the same mandatory-dependency
 * reasoning as {@link UiLabelHook}.
 */
@FunctionalInterface
public interface SessionActorHook {
    Optional<Long> getCurrentActorId();
}
