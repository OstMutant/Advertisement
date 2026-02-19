package org.ost.advertisement.ui.utils;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum NotificationType {
    SUCCESS(NotificationVariant.LUMO_SUCCESS),
    ERROR(NotificationVariant.LUMO_ERROR);

    private static final int DURATION_MS = 300_000;

    private final NotificationVariant variant;

    public void show(String text) {
        Notification notification = buildNotification();
        notification.add(buildLayout(text, notification));
        notification.open();
    }

    private Notification buildNotification() {
        Notification notification = new Notification();
        notification.setDuration(DURATION_MS);
        notification.setPosition(Notification.Position.BOTTOM_START);
        notification.addThemeVariants(variant);
        return notification;
    }

    private static HorizontalLayout buildLayout(String text, Notification notification) {
        HorizontalLayout layout = new HorizontalLayout(new Span(text), buildCloseButton(notification));
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        return layout;
    }

    private static Button buildCloseButton(Notification notification) {
        Button closeButton = new Button(new Icon("lumo", "cross"), _ -> notification.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        return closeButton;
    }
}