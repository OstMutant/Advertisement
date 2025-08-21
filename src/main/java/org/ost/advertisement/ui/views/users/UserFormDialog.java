package org.ost.advertisement.ui.views.users;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
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
import org.ost.advertisement.services.users.UserService;
import org.ost.advertisement.ui.utils.SessionUtil;
import org.ost.advertisement.ui.views.dialogs.BaseDialog;

@Slf4j
public class UserFormDialog extends BaseDialog {

	private final TextField nameField = createNameField();
	private final ComboBox<Role> roleCombo = createRoleCombo();

	private final Span idSpan = new Span();
	private final Span emailSpan = new Span();
	private final Component idComponent = createEmailComponent("ID:", idSpan);
	private final Component emailComponent = createEmailComponent("Email:", emailSpan);

	private final Span createdAtSpan = createDateSpan();
	private final Span updatedAtSpan = createDateSpan();
	private final Component createdAtComponent = createDateComponent("Created At:", createdAtSpan);
	private final Component updatedAtComponent = createDateComponent("Updated At:", updatedAtSpan);

	private final Binder<User> binder;

	private final UserService userService;
	private final User user;

	public UserFormDialog(User user, UserService userService) {
		super();
		this.user = user;
		this.userService = userService;

		binder = createBinder(user, nameField, roleCombo);

		idSpan.setText(String.valueOf(user.getId()));
		emailSpan.setText(ofNullable(user.getEmail()).orElse(""));
		createdAtSpan.setText(formatDate(user.getCreatedAt()));
		updatedAtSpan.setText(formatDate(user.getUpdatedAt()));

		title.setText("Edit User");
		actionsFooter.add(createSaveButton(event -> saveUser()), createCancelButton());

		content.add(idComponent, emailComponent, nameField, roleCombo, createdAtComponent, updatedAtComponent);
	}

	private TextField createNameField() {
		TextField field = new TextField("Name");
		field.setPlaceholder("Enter name");
		field.setRequired(true);
		field.setMaxLength(255);
		field.setAutofocus(true);
		return field;
	}

	private ComboBox<Role> createRoleCombo() {
		ComboBox<Role> combo = new ComboBox<>("Role");
		combo.setItems(Arrays.asList(Role.values()));
		combo.setRequired(true);
		combo.setAllowCustomValue(false);
		combo.setMinWidth("110px");
		combo.setMaxWidth("160px");
		return combo;
	}

	public Binder<User> createBinder(User user, TextField nameField, ComboBox<Role> roleCombo) {
		Binder<User> binder = new Binder<>(User.class);
		binder.setBean(user);

		binder.forField(nameField)
			.asRequired("Name cannot be empty")
			.withValidator(new StringLengthValidator("Name must be between 1 and 255 characters", 1, 255))
			.bind(User::getName, User::setName);

		binder.forField(roleCombo)
			.asRequired("Role is required")
			.bind(User::getRole, User::setRole);

		return binder;
	}

	private void saveUser() {
		try {
			binder.writeBean(user);
			userService.save(SessionUtil.getCurrentUser(), user);
			Notification.show("User updated", 3000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
			close();
		} catch (ValidationException e) {
			log.warn("Validation error: {}", e.getMessage());
			Notification.show("Validation failed", 5000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		} catch (Exception e) {
			log.error("Save failed", e);
			Notification.show("Save error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		}
	}

	private String formatDate(Instant instant) {
		return formatInstant(instant, "—");
	}
}
