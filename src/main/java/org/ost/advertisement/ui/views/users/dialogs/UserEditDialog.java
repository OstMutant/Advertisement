package org.ost.advertisement.ui.views.users.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.dto.UserEditDto;
import org.ost.advertisement.ui.mappers.UserMapper;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogDelegate;
import org.ost.advertisement.ui.views.components.dialogs.fields.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.constants.I18nKey.*;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

@SpringComponent
@Scope("prototype")
@Slf4j
@RequiredArgsConstructor
public class UserEditDialog {

    private final UserService userService;
    private final UserMapper mapper;
    private final LabeledField.Builder labeledFieldBuilder;
    private final I18nService i18n;
    @Getter
    private final FormDialogDelegate<UserEditDto> delegate;

    private void configureDialog() {
        UserEditDto user = delegate.getDto();
        delegate.setTitle(i18n.get(USER_DIALOG_TITLE));
        initFormFields(user);
        initActions();
    }

    private void initFormFields(UserEditDto user) {
        DialogTextField nameField = new DialogTextField(DialogTextField.Parameters.builder()
                .i18n(i18n)
                .labelKey(USER_DIALOG_FIELD_NAME_LABEL)
                .placeholderKey(USER_DIALOG_FIELD_NAME_PLACEHOLDER)
                .maxLength(255)
                .required(true)
                .build());

        DialogComboBox<Role> roleCombo = new DialogComboBox<>(DialogComboBox.Parameters.<Role>builder()
                .i18n(i18n)
                .labelKey(USER_DIALOG_FIELD_ROLE_LABEL)
                .items(Arrays.asList(Role.values()))
                .required(true)
                .build());

        delegate.getBinder().forField(nameField)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_NAME_REQUIRED))
                .withValidator(new StringLengthValidator(i18n.get(USER_DIALOG_VALIDATION_NAME_LENGTH), 1, 255))
                .bind(UserEditDto::getName, UserEditDto::setName);

        delegate.getBinder().forField(roleCombo)
                .asRequired(i18n.get(USER_DIALOG_VALIDATION_ROLE_REQUIRED))
                .bind(UserEditDto::getRole, UserEditDto::setRole);

        delegate.addContent(
                metadataField(USER_DIALOG_FIELD_ID_LABEL,    String.valueOf(user.getId()),               "email-label"),
                metadataField(USER_DIALOG_FIELD_EMAIL_LABEL, ofNullable(user.getEmail()).orElse(""),     "email-label"),
                nameField,
                roleCombo,
                metadataField(USER_DIALOG_FIELD_CREATED_LABEL, formatInstant(user.getCreatedAt()), "gray-label"),
                metadataField(USER_DIALOG_FIELD_UPDATED_LABEL, formatInstant(user.getUpdatedAt()), "gray-label")
        );
    }

    private Component metadataField(I18nKey label, String value, String colorClass) {
        return labeledFieldBuilder
                .withLabel(label)
                .withValue(value)
                .withCssClasses("base-label", colorClass)
                .build();
    }

    private void initActions() {
        DialogPrimaryButton saveButton = new DialogPrimaryButton(DialogPrimaryButton.Parameters.builder()
                .i18n(i18n).labelKey(USER_DIALOG_BUTTON_SAVE).build());
        saveButton.addClickListener(_ -> delegate.save(
                u -> userService.save(mapper.toUser(u)),
                USER_DIALOG_NOTIFICATION_SUCCESS,
                USER_DIALOG_NOTIFICATION_SAVE_ERROR
        ));

        DialogTertiaryButton cancelButton = new DialogTertiaryButton(DialogTertiaryButton.Parameters.builder()
                .i18n(i18n).labelKey(USER_DIALOG_BUTTON_CANCEL).build());
        cancelButton.addClickListener(_ -> delegate.close());

        delegate.addActions(saveButton, cancelButton);
    }

    public void open() {
        delegate.open();
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

            UserEditDialog dialog = dialogProvider.getObject(
                    userService, mapper, labeledFieldBuilder, i18n, delegate);
            dialog.configureDialog();
            return dialog;
        }

        public UserEditDialog buildAndOpen(User user, Runnable refresh) {
            UserEditDialog dialog = build(user, refresh);
            dialog.open();
            return dialog;
        }
    }
}