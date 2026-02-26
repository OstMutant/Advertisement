package org.ost.advertisement.ui.views.users.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.components.fields.UiIconButton;
import org.ost.advertisement.ui.views.components.fields.UiLabeledField;
import org.ost.advertisement.ui.views.components.fields.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.overlay.ModeHandler;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserViewModeHandler implements ModeHandler {

    private final AccessEvaluator               access;
    private final ObjectProvider<UiLabeledField>  labeledFieldProvider;
    private final ObjectProvider<UiPrimaryButton> editButtonProvider;
    private final ObjectProvider<UiIconButton>    closeButtonProvider;

    private Parameters params;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull User     user;
        @NonNull Runnable onEdit;
        @NonNull Runnable onClose;
    }

    private UserViewModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    public void activate(OverlayLayout layout) {
        User user = params.getUser();

        UiLabeledField idField      = field(USER_DIALOG_FIELD_ID_LABEL,      String.valueOf(user.getId()));
        UiLabeledField nameField    = field(USER_DIALOG_FIELD_NAME_LABEL,     user.getName());
        UiLabeledField emailField   = field(USER_DIALOG_FIELD_EMAIL_LABEL,    user.getEmail());
        UiLabeledField roleField    = field(USER_DIALOG_FIELD_ROLE_LABEL,     user.getRole().name());
        UiLabeledField createdField = field(USER_DIALOG_FIELD_CREATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getCreatedAt()));
        UiLabeledField updatedField = field(USER_DIALOG_FIELD_UPDATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getUpdatedAt()));

        UiPrimaryButton editButton = editButtonProvider.getObject().configure(
                UiPrimaryButton.Parameters.builder().labelKey(USER_VIEW_BUTTON_EDIT).build());
        UiIconButton closeButton = closeButtonProvider.getObject().configure(
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

    private UiLabeledField field(org.ost.advertisement.constants.I18nKey labelKey, String value) {
        return labeledFieldProvider.getObject().configure(
                UiLabeledField.Parameters.builder()
                        .labelKey(labelKey)
                        .value(value)
                        .build());
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<UserViewModeHandler> provider;

        public UserViewModeHandler build(Parameters p) {
            return provider.getObject().configure(p);
        }
    }
}
