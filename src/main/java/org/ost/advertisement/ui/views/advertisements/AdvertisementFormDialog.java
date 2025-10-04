package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.TailwindStyle;
import org.ost.advertisement.ui.views.components.dialogs.GenericFormDialog;

@Slf4j
public class AdvertisementFormDialog extends GenericFormDialog<Advertisement> {

	public AdvertisementFormDialog(Advertisement advertisement, AdvertisementService advertisementService,
								   I18nService i18n) {
		super(advertisement == null ? new Advertisement(AuthUtil.getCurrentUser().getId()) : advertisement,
			Advertisement.class, i18n);

		TextField titleField = createTitleField();
		TextArea descriptionField = createDescriptionField();

		binder.forField(titleField)
			.asRequired(i18n.get("advertisement.dialog.validation.title.required"))
			.withValidator(new StringLengthValidator(i18n.get("advertisement.dialog.validation.title.length"), 1, 255))
			.bind(Advertisement::getTitle, Advertisement::setTitle);

		binder.forField(descriptionField)
			.asRequired(i18n.get("advertisement.dialog.validation.description.required"))
			.bind(Advertisement::getDescription, Advertisement::setDescription);

		setTitle(dto.getId() == null
			? "advertisement.dialog.title.new"
			: "advertisement.dialog.title.edit");

		addContent(
			titleField,
			descriptionField,
			labeled("advertisement.dialog.field.created", formatDate(dto.getCreatedAt()), TailwindStyle.GRAY_LABEL),
			labeled("advertisement.dialog.field.updated", formatDate(dto.getUpdatedAt()), TailwindStyle.GRAY_LABEL),
			labeled("advertisement.dialog.field.user", String.valueOf(dto.getUserId()), TailwindStyle.EMAIL_LABEL)
		);

		addActions(
			createSaveButton("advertisement.dialog.button.save",
				event -> save(
					d -> advertisementService.save(AuthUtil.getCurrentUser(), d),
					"advertisement.dialog.notification.success",
					"advertisement.dialog.notification.save.error")),
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
}
