package org.ost.advertisement.ui.services;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final I18nService i18n;

    public void show(NotificationType type, I18nKey key, Object... args) {
        show(type, i18n.get(key, args));
    }

    public void show(NotificationType type, String message) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.access(() -> buildAndOpen(type, message));
        } else {
            buildAndOpen(type, message);
        }
    }

    private void buildAndOpen(NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setDuration(type.getDuration());
        notification.addThemeVariants(type.getVariant());
        notification.setPosition(Notification.Position.TOP_END);

        Icon icon = new Icon("lumo", type.getIconName());
        icon.addClassName("notification-icon");

        Div text = new Div(new Text(message));
        text.addClassName("notification-text");

        Button closeButton = new Button(new Icon("lumo", "cross"), _ -> notification.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

        HorizontalLayout layout = new HorizontalLayout(icon, text, closeButton);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.addClassName("notification-layout");

        notification.add(layout);
        notification.open();
    }
}