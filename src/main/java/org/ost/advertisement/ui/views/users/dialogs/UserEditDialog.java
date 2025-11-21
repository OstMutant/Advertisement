package org.ost.advertisement.ui.views.users.dialogs;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_BUTTON_CANCEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_BUTTON_SAVE;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_CREATED_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_EMAIL_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_ID_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_NAME_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_NAME_PLACEHOLDER;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_ROLE_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_UPDATED_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_NOTIFICATION_SAVE_ERROR;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_NOTIFICATION_SUCCESS;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_TITLE;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_VALIDATION_NAME_LENGTH;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_VALIDATION_NAME_REQUIRED;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_VALIDATION_ROLE_REQUIRED;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;
import static org.ost.advertisement.ui.views.TailwindStyle.EMAIL_LABEL;
import static org.ost.advertisement.ui.views.TailwindStyle.GRAY_LABEL;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import java.util.Arrays;
import java.util.Objects;
import lombok.AllArgsConstructor;
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
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@Slf4j
@AllArgsConstructor
public class UserEditDialog {

	private final UserService userService;
	private final UserMapper mapper;
	private final FormDialogDelegate.Builder<UserEditDto> delegateBuilder;
	private final LabeledField.Builder labeledFieldBuilder;
	private final I18nService i18n;

	public void openEdit(User user, Runnable refresh) {
		UserEditDto dto = mapper.toUserEdit(Objects.requireNonNull(user));
		FormDialogDelegate<UserEditDto> delegate = buildDelegate(dto, refresh);
		delegate.setTitle(i18n.get(USER_DIALOG_TITLE));
		configureDialog(delegate, dto);
		delegate.open();
	}

	private FormDialogDelegate<UserEditDto> buildDelegate(UserEditDto dto, Runnable refresh) {
		return delegateBuilder
			.withClass(UserEditDto.class)
			.withDto(dto)
			.withRefresh(refresh)
			.build();
	}

	private void configureDialog(FormDialogDelegate<UserEditDto> delegate, UserEditDto user) {
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
}
