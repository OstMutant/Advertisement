package org.ost.advertisement.ui.views.users.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.dto.UserEditDto;
import org.ost.advertisement.ui.mappers.UserMapper;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogBinder;
import org.ost.advertisement.ui.views.components.fields.ComboBox;
import org.ost.advertisement.ui.views.components.fields.PrimaryButton;
import org.ost.advertisement.ui.views.components.fields.TertiaryButton;
import org.ost.advertisement.ui.views.components.fields.TextField;
import org.ost.advertisement.ui.views.components.overlay.ModeHandler;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserFormModeHandler implements ModeHandler {

    private final UserService                           userService;
    private final UserMapper                            mapper;
    private final I18nService                           i18n;
    private final FormDialogBinder.Builder<UserEditDto> binderBuilder;
    @Qualifier("textField")
    private final TextField                             nameField;
    @Qualifier("comboBox")
    private final ComboBox<Role>                        roleComboBox;
    private final ObjectProvider<PrimaryButton>         saveButtonProvider;
    private final ObjectProvider<TertiaryButton>        cancelButtonProvider;

    private Parameters params;
    private FormDialogBinder<UserEditDto> binder;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull User     user;
        @NonNull Runnable onSave;
        @NonNull Runnable onCancel;
    }

    private UserFormModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    public void activate(OverlayLayout layout) {
        nameField.configure(TextField.Parameters.builder()
                .labelKey(USER_DIALOG_FIELD_NAME_LABEL)
                .placeholderKey(USER_DIALOG_FIELD_NAME_PLACEHOLDER)
                .maxLength(255)
                .required(true)
                .build());

        roleComboBox.configure(ComboBox.Parameters.<Role>builder()
                .labelKey(USER_DIALOG_FIELD_ROLE_LABEL)
                .items(Arrays.asList(Role.values()))
                .required(true)
                .build());

        UserEditDto dto = mapper.toUserEdit(params.getUser());
        buildBinder(dto);

        PrimaryButton saveButton = saveButtonProvider.getObject().configure(
                PrimaryButton.Parameters.builder().labelKey(USER_DIALOG_BUTTON_SAVE).build());
        TertiaryButton cancelButton = cancelButtonProvider.getObject().configure(
                TertiaryButton.Parameters.builder().labelKey(USER_DIALOG_BUTTON_CANCEL).build());

        saveButton.addClickListener(_  -> params.getOnSave().run());
        cancelButton.addClickListener(_ -> params.getOnCancel().run());

        layout.setContent(new Div(nameField, roleComboBox));
        layout.setHeaderActions(new Div(saveButton, cancelButton));
    }

    public boolean save() {
        return binder.save(dto -> userService.save(mapper.toUser(dto)));
    }

    private void buildBinder(UserEditDto dto) {
        binder = binderBuilder.build(
                FormDialogBinder.Config.<UserEditDto>builder()
                        .clazz(UserEditDto.class)
                        .dto(dto)
                        .build()
        );
        binder.getBinder().forField(nameField)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_NAME_REQUIRED))
                .withValidator(new StringLengthValidator(
                        i18n.get(USER_DIALOG_VALIDATION_NAME_LENGTH), 1, 255))
                .bind(UserEditDto::getName, UserEditDto::setName);
        binder.getBinder().forField(roleComboBox)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_ROLE_REQUIRED))
                .bind(UserEditDto::getRole, UserEditDto::setRole);
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<UserFormModeHandler> provider;

        public UserFormModeHandler build(Parameters p) {
            return provider.getObject().configure(p);
        }
    }
}
