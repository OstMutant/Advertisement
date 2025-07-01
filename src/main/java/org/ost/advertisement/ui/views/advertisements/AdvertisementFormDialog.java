package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.LongRangeValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;

import java.time.Instant;

@Slf4j
public class AdvertisementFormDialog extends Dialog {

	private final AdvertisementRepository advertisementRepository;
	private final Binder<Advertisement> binder = new Binder<>(Advertisement.class);
	private Advertisement currentAdvertisement;

	private TextField titleField;
	private TextArea descriptionField;
	private TextField categoryField;
	private TextField locationField;
	private TextField contactInfoField;
	private TextField imageUrlsField;
	private Select<String> statusField;
	private NumberField userIdField;

	public AdvertisementFormDialog(Advertisement advertisement, AdvertisementRepository advertisementRepository) {
		this.advertisementRepository = advertisementRepository;
		this.currentAdvertisement = (advertisement == null) ? new Advertisement() : advertisement;

		setHeaderTitle((advertisement == null) ? "Add Advertisement" : "Edit Advertisement");
		setCloseOnEsc(true);
		setCloseOnOutsideClick(true);

		createDialogLayout();
		setupBinder();
		populateForm();
	}

	private void createDialogLayout() {
		titleField = new TextField("Title");
		titleField.setPlaceholder("Enter title");
		titleField.setRequired(true);
		titleField.setMaxLength(255);
		titleField.setAutofocus(true);

		descriptionField = new TextArea("Description");
		descriptionField.setPlaceholder("Enter description");
		descriptionField.setHeight("100px");

		categoryField = new TextField("Category");
		categoryField.setPlaceholder("e.g., Electronics, Real Estate");

		locationField = new TextField("Location");
		locationField.setPlaceholder("e.g., Kyiv, Lviv");

		contactInfoField = new TextField("Contact Info");
		contactInfoField.setPlaceholder("e.g., +380XXXXXXXXX, email@example.com");

		imageUrlsField = new TextField("Image URLs (comma-separated)");
		imageUrlsField.setPlaceholder("e.g., url1,url2");

		statusField = new Select<>();
		statusField.setLabel("Status");
		statusField.setItems("ACTIVE", "EXPIRED", "DRAFT", "SOLD");
		statusField.setValue("ACTIVE");

		userIdField = new NumberField("User ID (Author)");
		userIdField.setPlaceholder("Enter user ID");
		userIdField.setMin(1);
		userIdField.setStep(1);

		FormLayout formLayout = new FormLayout(
			titleField, descriptionField, categoryField, locationField,
			contactInfoField, imageUrlsField, statusField, userIdField
		);
		formLayout.setResponsiveSteps(
			new FormLayout.ResponsiveStep("0", 1),
			new FormLayout.ResponsiveStep("500px", 2)
		);

		Button saveButton = new Button("Save");
		saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		saveButton.addClickListener(event -> saveAdvertisement());

		Button cancelButton = new Button("Cancel");
		cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		cancelButton.addClickListener(event -> close());

		HorizontalLayout buttonBar = new HorizontalLayout(saveButton, cancelButton);
		buttonBar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
		buttonBar.setWidthFull();

		add(formLayout, buttonBar);
	}

	private void setupBinder() {
		binder.forField(titleField)
			.asRequired("Title cannot be empty")
			.withValidator(new StringLengthValidator("Title must be between 1 and 255 characters", 1, 255))
			.bind(Advertisement::getTitle, Advertisement::setTitle);

		binder.forField(descriptionField).bind(Advertisement::getDescription, Advertisement::setDescription);
		binder.forField(categoryField).bind(Advertisement::getCategory, Advertisement::setCategory);
		binder.forField(locationField).bind(Advertisement::getLocation, Advertisement::setLocation);
		binder.forField(contactInfoField).bind(Advertisement::getContactInfo, Advertisement::setContactInfo);
		binder.forField(imageUrlsField).bind(Advertisement::getImageUrls, Advertisement::setImageUrls);
		binder.forField(statusField).bind(Advertisement::getStatus, Advertisement::setStatus);

		binder.forField(userIdField)
			.asRequired("User ID cannot be empty")
			.withConverter(
				value -> value == null ? null : value.longValue(), // From Double (field) to Long (bean)
				value -> value == null ? null : value.doubleValue(), // From Long (bean) to Double (field)
				"Invalid User ID format"
			)
			.withValidator(new LongRangeValidator("User ID must be a positive number", 1L, Long.MAX_VALUE))
			.bind(Advertisement::getUserId, Advertisement::setUserId);

		binder.setBean(currentAdvertisement);
	}

	private void populateForm() {
		// Binder handles population
	}

	private void saveAdvertisement() {
		try {
			binder.writeBean(currentAdvertisement);

			if (currentAdvertisement.getId() == null) {
				currentAdvertisement.setCreatedAt(Instant.now());
				currentAdvertisement.setUpdatedAt(Instant.now());
			} else {
				currentAdvertisement.setUpdatedAt(Instant.now());
			}

			advertisementRepository.save(currentAdvertisement);
			Notification.show("Advertisement saved successfully!", 3000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
			close();

		} catch (ValidationException e) {
			log.warn("Advertisement validation failed: {}", e.getMessage());
			Notification.show("Validation error: " + e.getLocalizedMessage(), 5000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		} catch (Exception e) {
			log.error("Failed to save advertisement: ", e);
			Notification.show("Error saving advertisement: " + e.getMessage(), 5000, Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		}
	}
}
