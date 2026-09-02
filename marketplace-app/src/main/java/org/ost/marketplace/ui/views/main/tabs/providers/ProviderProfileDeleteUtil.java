package org.ost.marketplace.ui.views.main.tabs.providers;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/**
 * Shared delete-confirmation flow for a provider profile in the public Providers catalog --
 * reused by both the catalog card and the catalog overlay, which offer the same delete action
 * from two different entry points into the same flow.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProviderProfileDeleteUtil {

    public static void confirmAndDelete(I18nService i18n, NotificationService notificationService,
                                         ProviderProfileSaveService providerProfileSaveService, AccessEvaluator access,
                                         ProviderProfileDto profile, Runnable onDeleted) {
        new ConfirmActionDialog(
                i18n.get(PROVIDERS_CATALOG_CONFIRM_DELETE_TITLE),
                i18n.get(PROVIDERS_CATALOG_CONFIRM_DELETE_TEXT),
                i18n.get(PROVIDERS_CATALOG_CONFIRM_DELETE_BUTTON),
                i18n.get(PROVIDERS_CATALOG_CONFIRM_CANCEL_BUTTON),
                () -> {
                    try {
                        providerProfileSaveService.delete(profile.getId(), access.getCurrentUserId(), profile.getVersion());
                        notificationService.success(PROVIDERS_CATALOG_NOTIFICATION_DELETED);
                        onDeleted.run();
                    } catch (Exception _) {
                        notificationService.error(PROVIDERS_CATALOG_NOTIFICATION_DELETE_ERROR);
                    }
                }
        ).open();
    }
}
