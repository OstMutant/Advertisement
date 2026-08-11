package org.ost.orchestrator.spi;

import lombok.NonNull;

/**
 * Hook: marketplace-orchestrator -> marketplace-app.
 * {@code UserActorNameService} calls this to resolve the "(deleted)" suffix appended to a
 * removed actor's display name -- the one remaining case where an orchestrator-owned class needs
 * a translated string, since its real caller (the audit-starter, via {@code AuditDomainHook}) has
 * no i18n awareness of its own. Marketplace-app implements it as a thin wrapper around its own
 * I18nService. Lives here, not platform-commons, because marketplace-orchestrator is a mandatory,
 * non-optional dependency of marketplace-app -- the "SPI must live in platform-commons" rule
 * exists for starter optionality, which doesn't apply to this caller/implementor pair.
 */
@FunctionalInterface
public interface UiLabelHook {
    String translateActorDeletedSuffix(@NonNull String actorName);
}
