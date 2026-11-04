package org.ost.marketplace.ui.views.components;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.platform.core.EditWindowClosedException;

import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_NOTIFICATION_EDIT_WINDOW_CLOSED;
import static org.ost.marketplace.services.i18n.I18nKey.FEEDBACK_NOTIFICATION_SAVE_ERROR;

/**
 * Shared error-handling wrapper for feedback/comment write actions triggered from a UI click
 * listener -- reused by {@link FeedbackPanel} and {@link FeedbackReplyPanel}, which offer the
 * same save/flag/delete actions from different entry points into the same feedback domain.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FeedbackErrorHandlingUtil {

    public static void runCatchingFeedbackErrors(@NonNull NotificationService notificationService, @NonNull Runnable action) {
        try {
            action.run();
        } catch (EditWindowClosedException _) {
            notificationService.error(FEEDBACK_NOTIFICATION_EDIT_WINDOW_CLOSED);
        } catch (Exception _) {
            notificationService.error(FEEDBACK_NOTIFICATION_SAVE_ERROR);
        }
    }
}
