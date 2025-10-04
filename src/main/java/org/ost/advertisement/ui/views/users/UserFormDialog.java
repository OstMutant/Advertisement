package org.ost.advertisement.ui.views.users;

import static java.util.Optional.ofNullable;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.views.TailwindStyle;
import org.ost.advertisement.ui.views.components.dialogs.GenericFormDialog;

@Slf4j
public class UserFormDialog extends GenericFormDialog<User> {

	public UserFormDialog(User user, UserService userService, I18nService i18n) {
		super(user, User.class, i18n);

		TextField nameField = createNameField();
		ComboBox<Role> roleCombo = createRoleCombo();

		binder.forField(nameField)
			.asRequired(i18n.get("user.dialog.validation.name.required"))
			.withValidator(new StringLengthValidator(i18n.get("user.dialog.validation.name.length"), 1, 255))
			.bind(User::getName, User::setName);

		binder.forField(roleCombo)
			.asRequired(i18n.get("user.dialog.validation.role.required"))
			.bind(User::getRole, User::setRole);

		setTitle("user.dialog.title");

		addContent(
			labeled("user.dialog.field.id.label", String.valueOf(user.getId()), TailwindStyle.EMAIL_LABEL),
			labeled("user.dialog.field.email.label", ofNullable(user.getEmail()).orElse(""), TailwindStyle.EMAIL_LABEL),
			nameField,
			roleCombo,
			labeled("user.dialog.field.created.label", formatDate(user.getCreatedAt()), TailwindStyle.GRAY_LABEL),
			labeled("user.dialog.field.updated.label", formatDate(user.getUpdatedAt()), TailwindStyle.GRAY_LABEL)
		);

		addActions(
			createSaveButton("user.dialog.button.save",
				event -> save(dto -> userService.save(AuthUtil.getCurrentUser(), dto),
					"user.dialog.notification.success", "user.dialog.notification.save.error")),
			createCancelButton("user.dialog.button.cancel")
		);
	}


	private TextField createNameField() {
		TextField field = new TextField(i18n.get("user.dialog.field.name.label"));
		field.setPlaceholder(i18n.get("user.dialog.field.name.placeholder"));
		field.setRequired(true);
		field.setMaxLength(255);
		return field;
	}

	private ComboBox<Role> createRoleCombo() {
		ComboBox<Role> combo = new ComboBox<>(i18n.get("user.dialog.field.role.label"));
		combo.setItems(Arrays.asList(Role.values()));
		combo.setRequired(true);
		combo.setAllowCustomValue(false);
		combo.setMinWidth("110px");
		combo.setMaxWidth("160px");
		return combo;
	}
}

