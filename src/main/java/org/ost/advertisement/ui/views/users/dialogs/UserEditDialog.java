package org.ost.advertisement.ui.views.users.dialogs;

import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.dto.UserEditDto;
import org.ost.advertisement.ui.mappers.UserMapper;
import org.ost.advertisement.ui.views.components.dialogs.BaseDialog;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogBinder;
import org.ost.advertisement.ui.views.users.dialogs.fields.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.Objects;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.constants.I18nKey.*;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstantHuman;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserEditDialog extends BaseDialog {

    private final transient UserService userService;
    private final transient UserMapper mapper;
    @Getter
    private final transient I18nService i18n;

    private final DialogUserIdLabeledField idField;
    private final DialogUserEmailLabeledField emailField;
    private final DialogUserCreatedAtLabeledField createdAtField;
    private final DialogUserUpdatedAtLabeledField updatedAtField;
    private final DialogUserNameTextField nameField;
    private final DialogUserRoleComboBox roleCombo;
    private final DialogUserSaveButton saveButton;
    private final DialogUserCancelButton cancelButton;

    @Getter
    private final transient DialogLayout layout;
    @Getter
    private transient FormDialogBinder<UserEditDto> binder;

    @Override
    @PostConstruct
    public void init() {
        super.init();
    }

    private void configure(FormDialogBinder<UserEditDto> binder) {
        this.binder = binder;
        bindFields();
        setTitle();
        updateMetadata();
        addContent();
        addActions();
    }

    private void bindFields() {
        binder.getBinder().forField(nameField)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_NAME_REQUIRED))
                .withValidator(new StringLengthValidator(i18n.get(USER_DIALOG_VALIDATION_NAME_LENGTH), 1, 255))
                .bind(UserEditDto::getName, UserEditDto::setName);

        binder.getBinder().forField(roleCombo)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_ROLE_REQUIRED))
                .bind(UserEditDto::getRole, UserEditDto::setRole);
    }

    private void setTitle() {
        setHeaderTitle(i18n.get(USER_DIALOG_TITLE));
    }

    private void updateMetadata() {
        UserEditDto user = binder.getDto();
        idField.update(String.valueOf(user.getId()));
        emailField.update(ofNullable(user.getEmail()).orElse(""));
        createdAtField.update(formatInstantHuman(user.getCreatedAt()));
        updatedAtField.update(formatInstantHuman(user.getUpdatedAt()));
    }

    private void addContent() {
        layout.addFormContent(nameField, roleCombo, emailField, idField, createdAtField, updatedAtField);
    }

    private void addActions() {
        saveButton.addClickListener(_ -> savedNotifier(
                binder.save(u -> userService.save(mapper.toUser(u))),
                USER_DIALOG_NOTIFICATION_SUCCESS,
                USER_DIALOG_NOTIFICATION_SAVE_ERROR
        ));
        cancelButton.addClickListener(_ -> close());

        getFooter().add(saveButton, cancelButton);
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {

        private final UserMapper mapper;
        private final FormDialogBinder.Builder<UserEditDto> dialogBinderBuilder;
        private final ObjectProvider<UserEditDialog> dialogProvider;

        public void buildAndOpen(User user, Runnable refresh) {
            build(user, refresh).open();
        }

        private UserEditDialog build(User user, Runnable refresh) {
            UserEditDialog dialog = dialogProvider.getObject();
            dialog.applyRefresh(refresh);
            dialog.configure(createBinder(user));
            return dialog;
        }

        private FormDialogBinder<UserEditDto> createBinder(User user) {
            UserEditDto editDto = mapper.toUserEdit(Objects.requireNonNull(user));
            return dialogBinderBuilder.build(FormDialogBinder.Config.<UserEditDto>builder()
                    .clazz(UserEditDto.class)
                    .dto(editDto)
                    .build());
        }
    }
}