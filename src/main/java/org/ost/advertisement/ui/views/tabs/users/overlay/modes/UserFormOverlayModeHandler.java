package org.ost.advertisement.ui.views.tabs.users.overlay.modes;

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
import org.ost.advertisement.ui.views.components.overlay.OverlayFormBinder;
import org.ost.advertisement.ui.views.components.fields.UiComboBox;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.buttons.UiTertiaryButton;
import org.ost.advertisement.ui.views.components.fields.UiTextField;
import org.ost.advertisement.ui.views.components.overlay.OverlayModeHandler;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserFormOverlayModeHandler implements OverlayModeHandler {

    private final UserService                           userService;
    private final UserMapper                            mapper;
    private final I18nService                           i18n;
    private final OverlayFormBinder.Builder<UserEditDto> binderBuilder;
    private final UiTextField                           nameField;
    private final UiComboBox<Role>                      roleComboBox;
    private final UiPrimaryButton.Builder               saveButtonBuilder;
    private final UiTertiaryButton.Builder              cancelButtonBuilder;

    private Parameters params;
    private OverlayFormBinder<UserEditDto> binder;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull User     user;
        @NonNull Runnable onSave;
        @NonNull Runnable onCancel;
    }

    private UserFormOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        nameField.configure(UiTextField.Parameters.builder()
                .labelKey(USER_DIALOG_FIELD_NAME_LABEL)
                .placeholderKey(USER_DIALOG_FIELD_NAME_PLACEHOLDER)
                .maxLength(255)
                .required(true)
                .build());

        roleComboBox.configure(UiComboBox.Parameters.<Role>builder()
                .labelKey(USER_DIALOG_FIELD_ROLE_LABEL)
                .items(Arrays.asList(Role.values()))
                .required(true)
                .build());

        UserEditDto dto = mapper.toUserEdit(params.getUser());
        buildBinder(dto);

        UiPrimaryButton saveButton = saveButtonBuilder.build(
                UiPrimaryButton.Parameters.builder().labelKey(USER_DIALOG_BUTTON_SAVE).build());
        UiTertiaryButton cancelButton = cancelButtonBuilder.build(
                UiTertiaryButton.Parameters.builder().labelKey(USER_DIALOG_BUTTON_CANCEL).build());

        saveButton.addClickListener(_  -> params.getOnSave().run());
        cancelButton.addClickListener(_ -> params.getOnCancel().run());

        layout.setContent(new Div(nameField, roleComboBox));
        layout.setHeaderActions(new Div(saveButton, cancelButton));
    }

    public boolean save() {
        return binder.save(dto -> userService.save(mapper.toUser(dto)));
    }

    public boolean hasChanges() {
        return binder != null && binder.hasChanges();
    }

    private void buildBinder(UserEditDto dto) {
        binder = binderBuilder.build(
                OverlayFormBinder.Config.<UserEditDto>builder()
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
        binder.readInitialValues();
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<UserFormOverlayModeHandler> provider;

        public UserFormOverlayModeHandler build(Parameters p) {
            return provider.getObject().configure(p);
        }
    }
}