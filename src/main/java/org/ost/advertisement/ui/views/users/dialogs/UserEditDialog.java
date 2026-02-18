package org.ost.advertisement.ui.views.users.dialogs;

import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.dto.UserEditDto;
import org.ost.advertisement.ui.mappers.UserMapper;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogDelegate;
import org.ost.advertisement.ui.views.users.dialogs.fields.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.Objects;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.constants.I18nKey.*;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstantHuman;

@SpringComponent
@Scope("prototype")
@Slf4j
@RequiredArgsConstructor
public class UserEditDialog {

    private final UserService userService;
    private final UserMapper mapper;
    private final I18nService i18n;
    private final DialogUserIdLabeledField idField;
    private final DialogUserEmailLabeledField emailField;
    private final DialogUserCreatedAtLabeledField createdAtField;
    private final DialogUserUpdatedAtLabeledField updatedAtField;
    private final DialogUserNameTextField nameField;
    private final DialogUserRoleComboBox roleCombo;
    private final DialogUserSaveButton saveButton;
    private final DialogUserCancelButton cancelButton;

    private FormDialogDelegate<UserEditDto> delegate;

    private void configureDialog(FormDialogDelegate<UserEditDto> delegate) {
        this.delegate = delegate;
        setTitle();
        bindFields();
        updateMetadata();
        addContent();
        addActions();
    }

    private void setTitle() {
        delegate.setTitle(i18n.get(USER_DIALOG_TITLE));
    }

    private void bindFields() {
        delegate.getBinder().forField(nameField)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_NAME_REQUIRED))
                .withValidator(new StringLengthValidator(i18n.get(USER_DIALOG_VALIDATION_NAME_LENGTH), 1, 255))
                .bind(UserEditDto::getName, UserEditDto::setName);

        delegate.getBinder().forField(roleCombo)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_ROLE_REQUIRED))
                .bind(UserEditDto::getRole, UserEditDto::setRole);
    }

    private void updateMetadata() {
        UserEditDto user = delegate.getDto();
        idField.update(String.valueOf(user.getId()));
        emailField.update(ofNullable(user.getEmail()).orElse(""));
        createdAtField.update(formatInstantHuman(user.getCreatedAt()));
        updatedAtField.update(formatInstantHuman(user.getUpdatedAt()));
    }

    private void addContent() {
        delegate.addContent(nameField, roleCombo, emailField, idField, createdAtField, updatedAtField);
    }

    private void addActions() {
        saveButton.addClickListener(_ -> delegate.save(
                u -> userService.save(mapper.toUser(u)),
                USER_DIALOG_NOTIFICATION_SUCCESS,
                USER_DIALOG_NOTIFICATION_SAVE_ERROR
        ));

        cancelButton.addClickListener(_ -> delegate.close());

        delegate.addActions(saveButton, cancelButton);
    }

    public void open() {
        delegate.open();
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {

        private final UserMapper mapper;
        private final FormDialogDelegate.Builder<UserEditDto> delegateBuilder;
        private final ObjectProvider<UserEditDialog> dialogProvider;

        public UserEditDialog build(User user, Runnable refresh) {
            FormDialogDelegate<UserEditDto> delegate = createDelegate(user, refresh);
            UserEditDialog dialog = dialogProvider.getObject();
            dialog.configureDialog(delegate);
            return dialog;
        }

        public UserEditDialog buildAndOpen(User user, Runnable refresh) {
            UserEditDialog dialog = build(user, refresh);
            dialog.open();
            return dialog;
        }

        private FormDialogDelegate<UserEditDto> createDelegate(User user, Runnable refresh) {
            return delegateBuilder
                    .withClass(UserEditDto.class)
                    .withDto(mapper.toUserEdit(Objects.requireNonNull(user)))
                    .withRefresh(refresh)
                    .build();
        }
    }
}
