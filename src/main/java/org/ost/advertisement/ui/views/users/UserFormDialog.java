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
import org.ost.advertisement.entyties.Role;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.UserRepository;
import org.ost.advertisement.ui.views.dialogs.BaseDialog;

@Slf4j
public class UserFormDialog extends BaseDialog {

	private final TextField nameField = createNameField();
	private final ComboBox<Role> roleCombo = createRoleCombo();
	private final Span createdAtSpan = createDateSpan();
	private final Span updatedAtSpan = createDateSpan();
	private final Component createdAtComponent = createDateComponent("Created At:", createdAtSpan);
	private final Component updatedAtComponent = createDateComponent("Updated At:", updatedAtSpan);
	private final Span emailSpan = createEmailSpan();
	private final Component emailComponent = createEmailComponent("Email:", emailSpan);

	private final Binder<User> binder = new Binder<>(User.class);

	private final UserRepository userRepository;
	private final User currentUser;

	public UserFormDialog(User user, UserRepository userRepository) {
		super();

		this.userRepository = userRepository;
		this.currentUser = user;

		configureBinder();

		title.setText("Edit User");
		actionsFooter.add(createSaveButton(event -> saveUser()), createCancelButton());
		content.add(emailComponent, nameField, roleCombo, createdAtComponent, updatedAtComponent);
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

	private void configureBinder() {
		binder.setBean(currentUser);

		binder.forField(nameField)
			.asRequired("Name cannot be empty")
			.withValidator(new StringLengthValidator("Name must be between 1 and 255 characters", 1, 255))
			.bind(User::getName, User::setName);

		binder.forField(roleCombo)
			.asRequired("Role is required")
			.bind(User::getRole, User::setRole);

		emailSpan.setText(ofNullable(currentUser.getEmail()).orElse(""));
		createdAtSpan.setText(formatDate(currentUser.getCreatedAt()));
		updatedAtSpan.setText(formatDate(currentUser.getUpdatedAt()));
	}

	private void saveUser() {
		try {
			binder.writeBean(currentUser);
			currentUser.setUpdatedAt(Instant.now());

			userRepository.save(currentUser);
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
