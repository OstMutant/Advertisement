package org.ost.advertisement.ui.views.users.dialogs;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;

import static org.ost.advertisement.constants.I18nKey.*;

public class UserViewDialog extends Dialog {

    public UserViewDialog(I18nService i18n, User user) {
        initDialog(i18n, user);

        VerticalLayout content = createContent(i18n, user);
        Button closeButton = createCloseButton(i18n);

        VerticalLayout layout = new VerticalLayout(content, closeButton);
        layout.addClassName("user-view-dialog-layout");
        layout.setPadding(false);

        add(layout);
    }

    private void initDialog(I18nService i18n, User user) {
        setHeaderTitle(i18n.get(USER_VIEW_DIALOG_TITLE) + " — " + user.getName());
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);
        addClassName("user-view-dialog");
    }

    private VerticalLayout createContent(I18nService i18n, User user) {
        VerticalLayout content = new VerticalLayout();
        content.addClassName("user-view-content");
        content.setSpacing(true);
        content.setPadding(false);

        content.add(
                createField(i18n.get(USER_VIEW_DIALOG_FIELD_ID), String.valueOf(user.getId())),
                createField(i18n.get(USER_VIEW_DIALOG_FIELD_NAME), user.getName()),
                createField(i18n.get(USER_VIEW_DIALOG_FIELD_EMAIL), user.getEmail()),
                createField(i18n.get(USER_VIEW_DIALOG_FIELD_ROLE), user.getRole().name()),
                createField(i18n.get(USER_VIEW_DIALOG_FIELD_CREATED),
                        TimeZoneUtil.formatInstantHuman(user.getCreatedAt())),
                createField(i18n.get(USER_VIEW_DIALOG_FIELD_UPDATED),
                        TimeZoneUtil.formatInstantHuman(user.getUpdatedAt()))
        );

        return content;
    }

    private HorizontalLayout createField(String label, String value) {
        Span labelSpan = new Span(label + ":");
        labelSpan.addClassName("user-view-field-label");

        Span valueSpan = new Span(value);
        valueSpan.addClassName("user-view-field-value");

        HorizontalLayout field = new HorizontalLayout(labelSpan, valueSpan);
        field.addClassName("user-view-field");
        field.setSpacing(true);

        return field;
    }

    private Button createCloseButton(I18nService i18n) {
        Button close = new Button(i18n.get(USER_VIEW_DIALOG_CLOSE), _ -> close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        close.addClassName("user-view-dialog-close");
        close.focus();
        return close;
    }
}