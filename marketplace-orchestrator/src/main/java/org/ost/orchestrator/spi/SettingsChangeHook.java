package org.ost.orchestrator.spi;

import lombok.NonNull;
import org.ost.platform.user.dto.UserSettingsDto;

/**
 * Hook: marketplace-orchestrator -> marketplace-app.
 * Forwards {@code UserSettingsChangedHook} events (fired by user-spring-boot-starter after a
 * settings save) to marketplace-app, which resets any cached UI state derived from settings
 * (e.g. pagination defaults) for the affected user's active session -- pure Vaadin UI-push work,
 * so it stays entirely in marketplace-app rather than being split like
 * {@code AdvertisementAuditEnrichService}.
 */
@FunctionalInterface
public interface SettingsChangeHook {
    void onSettingsChanged(@NonNull Long userId, @NonNull UserSettingsDto settings);
}
