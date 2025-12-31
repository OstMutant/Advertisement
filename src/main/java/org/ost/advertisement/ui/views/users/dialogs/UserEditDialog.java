package org.ost.advertisement.ui.views.users.dialogs;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.dto.UserEditDto;
import org.ost.advertisement.ui.mappers.UserMapper;
import org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogDelegate;
import org.ost.advertisement.ui.views.components.dialogs.LabeledField;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.constants.I18nKey.*;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;
import static org.ost.advertisement.ui.views.TailwindStyle.EMAIL_LABEL;
import static org.ost.advertisement.ui.views.TailwindStyle.GRAY_LABEL;

@SpringComponent
@Scope("prototype")
@Slf4j
public class UserEditDialog {

    private final UserService userService;
    private final UserMapper mapper;
    private final LabeledField.Builder labeledFieldBuilder;
    private final I18nService i18n;
    @Getter
    private final FormDialogDelegate<UserEditDto> delegate;

    private UserEditDialog(UserService userService,
                           UserMapper mapper,
                           LabeledField.Builder labeledFieldBuilder,
                           I18nService i18n,
                           FormDialogDelegate<UserEditDto> delegate) {
        this.userService = userService;
        this.mapper = mapper;
        this.labeledFieldBuilder = labeledFieldBuilder;
        this.i18n = i18n;
        this.delegate = delegate;
    }

    private void configureDialog() {
        UserEditDto user = delegate.getDto();
        delegate.setTitle(i18n.get(USER_DIALOG_TITLE));

        TextField nameField = DialogContentFactory.textField(
                i18n, USER_DIALOG_FIELD_NAME_LABEL, USER_DIALOG_FIELD_NAME_PLACEHOLDER, 255, true
        );

        ComboBox<Role> roleCombo = DialogContentFactory.comboBox(
                i18n, USER_DIALOG_FIELD_ROLE_LABEL, Arrays.asList(Role.values()), true
        );

        delegate.getBinder().forField(nameField)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_NAME_REQUIRED))
                .withValidator(new StringLengthValidator(i18n.get(USER_DIALOG_VALIDATION_NAME_LENGTH), 1, 255))
                .bind(UserEditDto::getName, UserEditDto::setName);

        delegate.getBinder().forField(roleCombo)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_ROLE_REQUIRED))
                .bind(UserEditDto::getRole, UserEditDto::setRole);

        delegate.addContent(
                labeledFieldBuilder.withLabel(USER_DIALOG_FIELD_ID_LABEL)
                        .withValue(String.valueOf(user.getId()))
                        .withStyles(EMAIL_LABEL)
                        .build(),
                labeledFieldBuilder.withLabel(USER_DIALOG_FIELD_EMAIL_LABEL)
                        .withValue(ofNullable(user.getEmail()).orElse(""))
                        .withStyles(EMAIL_LABEL)
                        .build(),
                nameField,
                roleCombo,
                labeledFieldBuilder.withLabel(USER_DIALOG_FIELD_CREATED_LABEL)
                        .withValue(formatInstant(user.getCreatedAt()))
                        .withStyles(GRAY_LABEL)
                        .build(),
                labeledFieldBuilder.withLabel(USER_DIALOG_FIELD_UPDATED_LABEL)
                        .withValue(formatInstant(user.getUpdatedAt()))
                        .withStyles(GRAY_LABEL)
                        .build()
        );

        Button saveButton = DialogContentFactory.primaryButton(i18n, USER_DIALOG_BUTTON_SAVE);
        saveButton.addClickListener(event -> delegate.save(
                u -> userService.save(mapper.toUser(u)),
                USER_DIALOG_NOTIFICATION_SUCCESS,
                USER_DIALOG_NOTIFICATION_SAVE_ERROR
        ));

        Button cancelButton = DialogContentFactory.tertiaryButton(i18n, USER_DIALOG_BUTTON_CANCEL);
        cancelButton.addClickListener(event -> delegate.close());

        delegate.addActions(saveButton, cancelButton);
    }

    @SpringComponent
    public static class Builder {

        private final UserService userService;
        private final UserMapper mapper;
        private final LabeledField.Builder labeledFieldBuilder;
        private final I18nService i18n;
        private final FormDialogDelegate.Builder<UserEditDto> delegateBuilder;
        private final ObjectProvider<UserEditDialog> dialogProvider;

        public Builder(UserService userService,
                       UserMapper mapper,
                       LabeledField.Builder labeledFieldBuilder,
                       I18nService i18n,
                       FormDialogDelegate.Builder<UserEditDto> delegateBuilder,
                       ObjectProvider<UserEditDialog> dialogProvider) {
            this.userService = userService;
            this.mapper = mapper;
            this.labeledFieldBuilder = labeledFieldBuilder;
            this.i18n = i18n;
            this.delegateBuilder = delegateBuilder;
            this.dialogProvider = dialogProvider;
        }

        public UserEditDialog build(User user, Runnable refresh) {
            FormDialogDelegate<UserEditDto> delegate = delegateBuilder
                    .withClass(UserEditDto.class)
                    .withDto(mapper.toUserEdit(Objects.requireNonNull(user)))
                    .withRefresh(refresh)
                    .build();

            UserEditDialog dialog = dialogProvider.getObject(userService, mapper, labeledFieldBuilder, i18n, delegate);
            dialog.configureDialog();
            return dialog;
        }

        public UserEditDialog buildAndOpen(User user, Runnable refresh) {
            UserEditDialog dialog = build(user, refresh);
            dialog.getDelegate().open();
            return dialog;
        }
    }
}
