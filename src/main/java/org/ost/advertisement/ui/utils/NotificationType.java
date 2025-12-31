package org.ost.advertisement.ui.utils;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

public enum NotificationType {
    SUCCESS(NotificationVariant.LUMO_SUCCESS),
    ERROR(NotificationVariant.LUMO_ERROR);

    private final NotificationVariant variant;

    NotificationType(NotificationVariant variant) {
        this.variant = variant;
    }

    public void show(String text) {
        Notification notification = Notification.show(text, 4000, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(variant);
    }
}
