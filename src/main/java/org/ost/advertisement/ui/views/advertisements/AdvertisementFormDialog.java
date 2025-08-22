package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.StringLengthValidator;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.ui.views.dialogs.BaseDialog;

@Slf4j
public class AdvertisementFormDialog extends BaseDialog {

	private final TextField titleField = createTextField("Title");
	private final TextArea descriptionField = createTextArea("Description");

	private final Span createdAtSpan = createDateSpan();
	private final Span updatedAtSpan = createDateSpan();
	private final Span userIdSpan = createEmailSpan();

	private final Binder<Advertisement> binder = new Binder<>(Advertisement.class);

	private final AdvertisementService advertisementService;
	private final Advertisement ad;

	public AdvertisementFormDialog(Advertisement advertisement, AdvertisementService advertisementService) {
		super();

		this.advertisementService = advertisementService;
		this.ad = advertisement == null ? new Advertisement() : advertisement;

		configureBinder();

		title.setText(advertisement == null ? "New Advertisement" : "Edit Advertisement");
		actionsFooter.add(createSaveButton(event -> saveAdvertisement()), createCancelButton());

		content.add(
			titleField,
			descriptionField,
			createDateComponent("Created At:", createdAtSpan),
			createDateComponent("Updated At:", updatedAtSpan),
			createEmailComponent("User ID:", userIdSpan)
		);
	}

	private TextField createTextField(String label) {
		TextField field = new TextField(label);
		field.setRequired(true);
		field.setMaxLength(255);
		return field;
	}

	private TextArea createTextArea(String label) {
		TextArea area = new TextArea(label);
		area.setRequired(true);
		area.setMaxLength(1000);
		area.setHeight("120px");
		return area;
	}

	private ComboBox<String> createStatusCombo() {
		ComboBox<String> combo = new ComboBox<>("Status");
		combo.setItems("ACTIVE", "ARCHIVED", "PENDING");
		combo.setAllowCustomValue(false);
		combo.setRequired(true);
		combo.setMinWidth("110px");
		combo.setMaxWidth("160px");
		return combo;
	}

	private void configureBinder() {
		binder.setBean(ad);

		binder.forField(titleField)
			.asRequired("Title is required")
			.withValidator(new StringLengthValidator("Max 255 characters", 1, 255))
			.bind(Advertisement::getTitle, Advertisement::setTitle);

		binder.forField(descriptionField)
			.asRequired("Description required")
			.bind(Advertisement::getDescription, Advertisement::setDescription);

		createdAtSpan.setText(formatDate(ad.getCreatedAt()));
		updatedAtSpan.setText(formatDate(ad.getUpdatedAt()));
		userIdSpan.setText(String.valueOf(ad.getUserId()));
	}

	private void saveAdvertisement() {
		try {
			binder.writeBean(ad);
			advertisementService.save(AuthUtil.getCurrentUser(), ad);
			Notification.show("Advertisement saved", 3000, Notification.Position.BOTTOM_START)
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
