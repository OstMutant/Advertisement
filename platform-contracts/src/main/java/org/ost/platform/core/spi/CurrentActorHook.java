package org.ost.platform.core.spi;

import java.util.Optional;

/**
 * Hook: starter → marketplace.
 * Starters call this to obtain the ID of the actor performing the current request
 * (e.g. for audit log attribution). Marketplace implements it against its own auth context.
 * Injected via {@code ObjectProvider} — actor ID is absent when called outside a request context.
 */
@FunctionalInterface
public interface CurrentActorHook {
    Optional<Long> getCurrentActorId();
}
