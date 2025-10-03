package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

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
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.TailwindStyle;
import org.ost.advertisement.ui.views.components.dialogs.DialogForm;

@Slf4j
public class AdvertisementFormDialog extends DialogForm {

	private final transient AdvertisementService advertisementService;
	private final transient Advertisement advertisement;
	private final Binder<Advertisement> binder;

	public AdvertisementFormDialog(Advertisement advertisement, AdvertisementService advertisementService,
								   I18nService i18n) {
		super(i18n);
		this.advertisementService = advertisementService;
		this.advertisement = advertisement == null ? new Advertisement() : advertisement;
		TextField titleField = createTitleField();
		TextArea descriptionField = createDescriptionField();

		this.binder = createBinder(titleField, descriptionField);

		setTitle(
			this.advertisement.getId() == null ? "advertisement.dialog.title.new" : "advertisement.dialog.title.edit");

		addContent(
			titleField,
			descriptionField,
			labeled("advertisement.dialog.field.created", formatDate(this.advertisement.getCreatedAt()),
				TailwindStyle.GRAY_LABEL),
			labeled("advertisement.dialog.field.updated", formatDate(this.advertisement.getUpdatedAt()),
				TailwindStyle.GRAY_LABEL),
			labeled("advertisement.dialog.field.user", String.valueOf(this.advertisement.getUserId()),
				TailwindStyle.EMAIL_LABEL)
		);

		addActions(
			createSaveButton("advertisement.dialog.button.save", event -> saveAdvertisement()),
			createCancelButton("advertisement.dialog.button.cancel")
		);
	}

	private TextField createTitleField() {
		TextField field = new TextField(i18n.get("advertisement.dialog.field.title"));
		field.setRequired(true);
		field.setMaxLength(255);
		return field;
	}

	private TextArea createDescriptionField() {
		TextArea area = new TextArea(i18n.get("advertisement.dialog.field.description"));
		area.setRequired(true);
		area.setMaxLength(1000);
		area.setHeight("120px");
		return area;
	}

	private Binder<Advertisement> createBinder(TextField titleField, TextArea descriptionField) {
		Binder<Advertisement> newBinder = new Binder<>(Advertisement.class);
		newBinder.setBean(advertisement);

		newBinder.forField(titleField)
			.asRequired(i18n.get("advertisement.dialog.validation.title.required"))
			.withValidator(new StringLengthValidator(i18n.get("advertisement.dialog.validation.title.length"), 1, 255))
			.bind(Advertisement::getTitle, Advertisement::setTitle);

		newBinder.forField(descriptionField)
			.asRequired(i18n.get("advertisement.dialog.validation.description.required"))
			.bind(Advertisement::getDescription, Advertisement::setDescription);
		return newBinder;
	}

	private void saveAdvertisement() {
		try {
			binder.writeBean(advertisement);
			advertisementService.save(AuthUtil.getCurrentUser(), advertisement);
			Notification.show(i18n.get("advertisement.dialog.notification.success"), 3000,
					Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
			close();
		} catch (ValidationException e) {
			log.warn("Validation error: {}", e.getMessage());
			Notification.show(i18n.get("advertisement.dialog.notification.validation.failed"), 5000,
					Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		} catch (Exception e) {
			log.error("Save failed", e);
			Notification.show(i18n.get("advertisement.dialog.notification.save.error", e.getMessage()), 5000,
					Notification.Position.BOTTOM_START)
				.addThemeVariants(NotificationVariant.LUMO_ERROR);
		}
	}

	private String formatDate(Instant instant) {
		return formatInstant(instant, "—");
	}
}

