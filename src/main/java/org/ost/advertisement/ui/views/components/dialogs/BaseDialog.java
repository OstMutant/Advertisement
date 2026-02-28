package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.dialog.Dialog;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.services.NotificationService;

public abstract class BaseDialog extends Dialog {

    public abstract DialogLayout getLayout();

    public abstract I18nService getI18n();

    public abstract NotificationService getNotificationService();

    protected void init() {
        setDraggable(false);
        setResizable(false);
        setCloseOnOutsideClick(false);
        setCloseOnEsc(true);
        add(getLayout());
    }

    protected void applyRefresh(Runnable refresh) {
        if (refresh != null) {
            addOpenedChangeListener(event -> {
                if (!event.isOpened()) refresh.run();
            });
        }
    }

    protected void savedNotifier(boolean isSaved, I18nKey successKey, I18nKey errorKey) {
        if (isSaved) {
            close();
            getNotificationService().success(successKey);
        } else {
            getNotificationService().error(errorKey, "Validation failed");
        }
    }
}