package org.ost.marketplace.ui.views.services;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.springframework.stereotype.Service;

import static org.ost.marketplace.services.i18n.I18nKey.NOTIFICATION_CLOSE_TOOLTIP;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final I18nService i18n;
    private final UiComponentFactory<UiIconButton> iconButtonFactory;

    /**
     * Convenience method for success notifications
     */
    public void success(@NonNull I18nKey key, Object... args) {
        show(NotificationType.SUCCESS, key, args);
    }

    public void success(@NonNull String message) {
        show(NotificationType.SUCCESS, message);
    }

    /**
     * Convenience method for error notifications
     */
    public void error(@NonNull I18nKey key, Object... args) {
        show(NotificationType.ERROR, key, args);
    }

    public void error(@NonNull String message) {
        show(NotificationType.ERROR, message);
    }

    private void show(NotificationType type, I18nKey key, Object... args) {
        show(type, i18n.get(key, args));
    }

    private void show(NotificationType type, String message) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.access(() -> buildAndOpen(type, message));
        } else {
            buildAndOpen(type, message);
        }
    }

    private void buildAndOpen(NotificationType type, String message) {
        Notification notification = createNotification(type, message);
        notification.open();
    }

    private Notification createNotification(NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setDuration(type.getDuration());
        notification.addThemeVariants(type.getVariant());
        notification.setPosition(Notification.Position.BOTTOM_END);
        notification.add(createLayout(type, message, notification));
        return notification;
    }

    private HorizontalLayout createLayout(NotificationType type, String message, Notification notification) {
        Icon icon = new Icon("lumo", type.getIconName());
        icon.addClassName("notification-icon");

        Div text = new Div(new Text(message));
        text.addClassName("notification-text");

        UiIconButton closeButton = iconButtonFactory.build(
                UiIconButton.Parameters.builder().labelKey(NOTIFICATION_CLOSE_TOOLTIP).icon(VaadinIcon.CLOSE_SMALL.create()).build());
        closeButton.addClassName("notification-close-btn");
        closeButton.addClickListener(_ -> notification.close());

        HorizontalLayout layout = new HorizontalLayout(icon, text, closeButton);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.addClassName("notification-layout");
        return layout;
    }

    @Getter
    @RequiredArgsConstructor
    public enum NotificationType {
        SUCCESS(NotificationVariant.LUMO_SUCCESS, "check", 5000),
        ERROR(NotificationVariant.LUMO_ERROR, "exclamation-triangle", 0);

        private final NotificationVariant variant;
        private final String iconName;
        private final int duration;
    }
}
