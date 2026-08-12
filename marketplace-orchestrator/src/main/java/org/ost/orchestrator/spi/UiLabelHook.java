package org.ost.orchestrator.spi;

import lombok.NonNull;
import org.ost.platform.advertisement.model.AdKind;

/**
 * Hook: marketplace-orchestrator -> marketplace-app.
 * Forwards i18n lookups and presentation decisions for the orchestrator's audit-facing services,
 * which have no i18n or markup awareness of their own: {@code UserActorNameService} resolves the
 * "(deleted)" suffix appended to a removed actor's display name; {@code
 * AdvertisementAuditEnrichService} resolves an AdKind's display label, decorates a deleted
 * taxon's name for diff rendering, and resolves the "no media" placeholder text. Marketplace-app
 * implements it as a thin wrapper around its own I18nService. Lives here, not platform-commons,
 * because marketplace-orchestrator is a mandatory, non-optional dependency of marketplace-app --
 * the "SPI must live in platform-commons" rule exists for starter optionality, which doesn't
 * apply to this caller/implementor pair.
 */
public interface UiLabelHook {
    String translateActorDeletedSuffix(@NonNull String actorName);

    String labelFor(@NonNull AdKind kind);

    String markDeleted(@NonNull String name);

    String noMediaPlaceholder();
}
