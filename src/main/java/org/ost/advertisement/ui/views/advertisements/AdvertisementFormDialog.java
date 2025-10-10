package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_BUTTON_CANCEL;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_BUTTON_SAVE;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_FIELD_CREATED;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_FIELD_TITLE;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_FIELD_UPDATED;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_FIELD_USER;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_NOTIFICATION_SAVE_ERROR;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_NOTIFICATION_SUCCESS;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_TITLE_EDIT;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_TITLE_NEW;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_VALIDATION_DESCRIPTION_REQUIRED;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_VALIDATION_TITLE_LENGTH;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_DIALOG_VALIDATION_TITLE_REQUIRED;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEdit;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.views.TailwindStyle;
import org.ost.advertisement.ui.views.components.dialogs.GenericFormDialog;

@Slf4j
public class AdvertisementFormDialog extends GenericFormDialog<AdvertisementEdit> {

	private final AdvertisementMapper mapper;

	public AdvertisementFormDialog(AdvertisementEdit advertisement, AdvertisementService advertisementService,
								   I18nService i18n, AdvertisementMapper mapper) {
		super(advertisement == null ? new AdvertisementEdit() : advertisement, AdvertisementEdit.class, i18n);
		this.mapper = mapper;

		TextField titleField = createTitleField();
		TextArea descriptionField = createDescriptionField();

		binder.forField(titleField)
			.asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_REQUIRED))
			.withValidator(new StringLengthValidator(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_LENGTH), 1, 255))
			.bind(AdvertisementEdit::getTitle, AdvertisementEdit::setTitle);

		binder.forField(descriptionField)
			.asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_DESCRIPTION_REQUIRED))
			.bind(AdvertisementEdit::getDescription, AdvertisementEdit::setDescription);

		setTitle(dto.getId() == null ? ADVERTISEMENT_DIALOG_TITLE_NEW : ADVERTISEMENT_DIALOG_TITLE_EDIT);

		addContent(
			titleField,
			descriptionField,
			labeled(ADVERTISEMENT_DIALOG_FIELD_CREATED, formatDate(dto.getCreatedAt()), TailwindStyle.GRAY_LABEL),
			labeled(ADVERTISEMENT_DIALOG_FIELD_UPDATED, formatDate(dto.getUpdatedAt()), TailwindStyle.GRAY_LABEL),
			labeled(ADVERTISEMENT_DIALOG_FIELD_USER, String.valueOf(dto.getCreatedByUserId()), TailwindStyle.EMAIL_LABEL)
		);

		addActions(
			createSaveButton(ADVERTISEMENT_DIALOG_BUTTON_SAVE,
				event -> save(ad -> advertisementService.save(mapper.toAdvertisement(ad)),
					ADVERTISEMENT_DIALOG_NOTIFICATION_SUCCESS,
					ADVERTISEMENT_DIALOG_NOTIFICATION_SAVE_ERROR)),
			createCancelButton(ADVERTISEMENT_DIALOG_BUTTON_CANCEL)
		);
	}

	private TextField createTitleField() {
		TextField field = new TextField(i18n.get(ADVERTISEMENT_DIALOG_FIELD_TITLE));
		field.setRequired(true);
		field.setMaxLength(255);
		return field;
	}

	private TextArea createDescriptionField() {
		TextArea area = new TextArea(i18n.get(ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION));
		area.setRequired(true);
		area.setMaxLength(1000);
		area.setHeight("120px");
		return area;
	}
}

