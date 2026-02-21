package org.ost.advertisement.ui.utils;

import com.vaadin.flow.component.notification.NotificationVariant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    SUCCESS(NotificationVariant.LUMO_SUCCESS, "check", 5000),
    ERROR(NotificationVariant.LUMO_ERROR, "exclamation-triangle", 0);

    private final NotificationVariant variant;
    private final String iconName;
    private final int duration;
}