package org.ost.advertisement.ui.views.tabs.users.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.ui.views.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.components.buttons.UiIconButton;
import org.ost.advertisement.ui.views.components.fields.UiLabeledField;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.overlay.OverlayModeHandler;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserViewOverlayModeHandler implements OverlayModeHandler {

    private final AccessEvaluator          access;
    private final UiLabeledField.Builder   labeledFieldBuilder;
    private final UiPrimaryButton.Builder  editButtonBuilder;
    private final UiIconButton.Builder     closeButtonBuilder;

    private Parameters params;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull User     user;
        @NonNull Runnable onEdit;
        @NonNull Runnable onClose;
    }

    private UserViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        User user = params.getUser();

        UiLabeledField idField      = field(USER_DIALOG_FIELD_ID_LABEL,     String.valueOf(user.getId()));
        UiLabeledField nameField    = field(USER_DIALOG_FIELD_NAME_LABEL,    user.getName());
        UiLabeledField emailField   = field(USER_DIALOG_FIELD_EMAIL_LABEL,   user.getEmail());
        UiLabeledField roleField    = field(USER_DIALOG_FIELD_ROLE_LABEL,    user.getRole().name());
        UiLabeledField createdField = field(USER_DIALOG_FIELD_CREATED_LABEL, TimeZoneUtil.formatInstantHuman(user.getCreatedAt()));
        UiLabeledField updatedField = field(USER_DIALOG_FIELD_UPDATED_LABEL, TimeZoneUtil.formatInstantHuman(user.getUpdatedAt()));

        UiPrimaryButton editButton = editButtonBuilder.build(
                UiPrimaryButton.Parameters.builder().labelKey(USER_VIEW_BUTTON_EDIT).build());
        UiIconButton closeButton = closeButtonBuilder.build(
                UiIconButton.Parameters.builder()
                        .labelKey(MAIN_TAB_USERS)
                        .icon(VaadinIcon.CLOSE.create())
                        .build());

        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canOperate(user));

        layout.setContent(new Div(idField, nameField, emailField, roleField, createdField, updatedField));
        layout.setHeaderActions(new Div(editButton, closeButton));
    }

    private UiLabeledField field(I18nKey labelKey, String value) {
        return labeledFieldBuilder.build(
                UiLabeledField.Parameters.builder()
                        .labelKey(labelKey)
                        .value(value)
                        .build());
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<UserViewOverlayModeHandler> provider;

        public UserViewOverlayModeHandler build(Parameters p) {
            return provider.getObject().configure(p);
        }
    }
}
