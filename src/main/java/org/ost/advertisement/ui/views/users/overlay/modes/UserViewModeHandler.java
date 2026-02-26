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
import org.ost.advertisement.ui.views.components.fields.IconButton;
import org.ost.advertisement.ui.views.components.fields.LabeledField;
import org.ost.advertisement.ui.views.components.fields.PrimaryButton;
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
    private final ObjectProvider<LabeledField>  labeledFieldProvider;
    private final ObjectProvider<PrimaryButton> editButtonProvider;
    private final ObjectProvider<IconButton>    closeButtonProvider;

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

        LabeledField idField      = field(USER_DIALOG_FIELD_ID_LABEL,      String.valueOf(user.getId()));
        LabeledField nameField    = field(USER_DIALOG_FIELD_NAME_LABEL,     user.getName());
        LabeledField emailField   = field(USER_DIALOG_FIELD_EMAIL_LABEL,    user.getEmail());
        LabeledField roleField    = field(USER_DIALOG_FIELD_ROLE_LABEL,     user.getRole().name());
        LabeledField createdField = field(USER_DIALOG_FIELD_CREATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getCreatedAt()));
        LabeledField updatedField = field(USER_DIALOG_FIELD_UPDATED_LABEL,  TimeZoneUtil.formatInstantHuman(user.getUpdatedAt()));

        PrimaryButton editButton = editButtonProvider.getObject().configure(
                PrimaryButton.Parameters.builder().labelKey(USER_VIEW_BUTTON_EDIT).build());
        IconButton closeButton = closeButtonProvider.getObject().configure(
                IconButton.Parameters.builder()
                        .labelKey(MAIN_TAB_USERS)
                        .icon(VaadinIcon.CLOSE.create())
                        .build());

        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canOperate(user));

        layout.setContent(new Div(idField, nameField, emailField, roleField, createdField, updatedField));
        layout.setHeaderActions(new Div(editButton, closeButton));
    }

    private LabeledField field(org.ost.advertisement.constants.I18nKey labelKey, String value) {
        return labeledFieldProvider.getObject().configure(
                LabeledField.Parameters.builder()
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
