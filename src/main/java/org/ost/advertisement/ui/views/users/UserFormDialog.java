package org.ost.advertisement.ui.views.users;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.StringLengthValidator;
import java.time.Instant;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.views.TailwindStyle;
import org.ost.advertisement.ui.views.components.dialogs.DialogForm;

@Slf4j
public class UserFormDialog extends DialogForm {

	private final transient UserService userService;
	private final transient User user;
	private final Binder<User> binder;


	public UserFormDialog(User user, UserService userService, I18nService i18n) {
		super(i18n);
		this.user = user;
		this.userService = userService;

		TextField nameField = createNameField();
		ComboBox<Role> roleCombo = createRoleCombo();
		binder = createBinder(nameField, roleCombo);

		setTitle("user.dialog.title");

		Component idComponent =
			labeled("user.dialog.field.id.label", String.valueOf(user.getId()), TailwindStyle.EMAIL_LABEL );
		Component emailComponent =
			labeled("user.dialog.field.email.label", ofNullable(user.getEmail()).orElse(""), TailwindStyle.EMAIL_LABEL);
		Component createdAtComponent =
			labeled("user.dialog.field.created.label", formatDate(user.getCreatedAt()), TailwindStyle.GRAY_LABEL);
		Component updatedAtComponent =
			labeled("user.dialog.field.updated.label", formatDate(user.getUpdatedAt()), TailwindStyle.GRAY_LABEL);
		addContent(idComponent, emailComponent, nameField, roleCombo, createdAtComponent, updatedAtComponent);

		addActions(createSaveButton("user.dialog.button.save", event -> saveUser()),
			createCancelButton("user.dialog.button.cancel"));

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

	private Binder<User> createBinder(TextField nameField, ComboBox<Role> roleCombo) {
		Binder<User> newBinder = new Binder<>(User.class);
		newBinder.setBean(user);

		newBinder.forField(nameField)
			.asRequired(i18n.get("user.dialog.validation.name.required"))
			.withValidator(new StringLengthValidator(i18n.get("user.dialog.validation.name.length"), 1, 255))
			.bind(User::getName, User::setName);

		newBinder.forField(roleCombo)
			.asRequired(i18n.get("user.dialog.validation.role.required"))
			.bind(User::getRole, User::setRole);

		return newBinder;
	}

	private void saveUser() {
		try {
			binder.writeBean(user);
			userService.save(AuthUtil.getCurrentUser(), user);
			Notification.show(i18n.get("user.dialog.notification.success"), 3000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
			close();
		} catch (ValidationException e) {
			log.warn("Validation error: {}", e.getMessage());
			Notification.show(i18n.get("user.dialog.notification.validation.failed"), 5000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		} catch (Exception e) {
			log.error("Save failed", e);
			Notification.show(i18n.get("user.dialog.notification.save.error", e.getMessage()), 5000,
					Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		}
	}

	private String formatDate(Instant instant) {
		return formatInstant(instant, "—");
	}
}

