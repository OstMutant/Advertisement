package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.StringLengthValidator;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.UserRepository;

@Slf4j
public class UserFormDialog extends Dialog {

	private final UserRepository userRepository;
	private final Binder<User> binder = new Binder<>(User.class);
	private User currentUser;

	private TextField nameField;

	public UserFormDialog(User user, UserRepository userRepository) {
		this.userRepository = userRepository;
		this.currentUser = (user == null) ? new User() : user;

		setHeaderTitle((user == null) ? "Add User" : "Edit User");
		setCloseOnEsc(true);
		setCloseOnOutsideClick(true);

		createDialogLayout();
		setupBinder();
		populateForm();
	}

	private void createDialogLayout() {
		nameField = new TextField("Name");
		nameField.setPlaceholder("Enter name");
		nameField.setRequired(true);
		nameField.setMaxLength(255);
		nameField.setAutofocus(true);

		FormLayout formLayout = new FormLayout(nameField);
		formLayout.setResponsiveSteps(
			new FormLayout.ResponsiveStep("0", 1),
			new FormLayout.ResponsiveStep("500px", 1)
		);

		Button saveButton = new Button("Save");
		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> saveUser());

		Button cancelButton = new Button("Cancel");
		cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		cancelButton.addClickListener(event -> close());

		HorizontalLayout buttonBar = new HorizontalLayout(saveButton, cancelButton);
		buttonBar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
		buttonBar.setWidthFull();

		add(formLayout, buttonBar);
	}

	private void setupBinder() {
		binder.forField(nameField)
			.asRequired("Name cannot be empty")
			.withValidator(new StringLengthValidator("Name must be between 1 and 255 characters", 1, 255))
			.bind(User::getName, User::setName);

		binder.setBean(currentUser);
	}

	private void populateForm() {
		// Binder handles population
	}

	private void saveUser() {
		try {
			binder.writeBean(currentUser);

			if (currentUser.getId() == null) {
				currentUser.setCreatedAt(Instant.now());
				currentUser.setUpdatedAt(Instant.now());
			} else {
				currentUser.setUpdatedAt(Instant.now());
			}

			userRepository.save(currentUser);
			Notification.show("User saved successfully!", 3000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
			close();

		} catch (ValidationException e) {
			log.warn("User validation failed: {}", e.getMessage());
			Notification.show("Validation error: " + e.getLocalizedMessage(), 5000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		} catch (Exception e) {
			log.error("Failed to save user: ", e);
			Notification.show("Error saving user: " + e.getMessage(), 5000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		}
	}
}
