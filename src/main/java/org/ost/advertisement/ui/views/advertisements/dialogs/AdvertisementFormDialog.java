package org.ost.advertisement.ui.views.advertisements.dialogs;

import static org.ost.advertisement.constants.I18nKey.*;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.views.TailwindStyle;
import org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory;
import org.ost.advertisement.ui.views.components.dialogs.GenericFormDialog;
import org.ost.advertisement.ui.views.components.dialogs.LabeledField;

@SpringComponent
@UIScope
@Slf4j
public class AdvertisementFormDialog extends GenericFormDialog<AdvertisementEditDto> {

	private final AdvertisementService advertisementService;
	private final AdvertisementMapper mapper;

	public AdvertisementFormDialog(AdvertisementService advertisementService,
								   I18nService i18n,
								   AdvertisementMapper mapper) {
		super(AdvertisementEditDto.class, i18n);
		this.advertisementService = advertisementService;
		this.mapper = mapper;
	}

	@Override
	public void open() {
		open(new AdvertisementEditDto());
	}

	public void open(AdvertisementEditDto dto) {
		dto = dto == null ? new AdvertisementEditDto() : dto;
		init(dto);
		setTitle(dto.getId() == null ? ADVERTISEMENT_DIALOG_TITLE_NEW : ADVERTISEMENT_DIALOG_TITLE_EDIT);

		TextField titleField = DialogContentFactory.textField(
			i18n, ADVERTISEMENT_DIALOG_FIELD_TITLE, ADVERTISEMENT_DIALOG_FIELD_TITLE, 255, true
		);

		TextArea descriptionField = DialogContentFactory.textArea(
			i18n, ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION, ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION, 1000, true
		);

		binder.forField(titleField)
			.asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_REQUIRED))
			.withValidator(new StringLengthValidator(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_LENGTH), 1, 255))
			.bind(AdvertisementEditDto::getTitle, AdvertisementEditDto::setTitle);

		binder.forField(descriptionField)
			.asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_DESCRIPTION_REQUIRED))
			.bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);

		LabeledField createdField = new LabeledField(i18n, TailwindStyle.GRAY_LABEL);
		createdField.set(ADVERTISEMENT_DIALOG_FIELD_CREATED, formatDate(dto.getCreatedAt()));

		LabeledField updatedField = new LabeledField(i18n, TailwindStyle.GRAY_LABEL);
		updatedField.set(ADVERTISEMENT_DIALOG_FIELD_UPDATED, formatDate(dto.getUpdatedAt()));

		LabeledField userField = new LabeledField(i18n, TailwindStyle.EMAIL_LABEL);
		userField.set(ADVERTISEMENT_DIALOG_FIELD_USER, String.valueOf(dto.getCreatedByUserId()));

		addContent(titleField, descriptionField, createdField, updatedField, userField);

		Button saveButton = DialogContentFactory.primaryButton(i18n, ADVERTISEMENT_DIALOG_BUTTON_SAVE);
		saveButton.addClickListener(event -> save(ad -> advertisementService.save(mapper.toAdvertisement(ad)),
			ADVERTISEMENT_DIALOG_NOTIFICATION_SUCCESS,
			ADVERTISEMENT_DIALOG_NOTIFICATION_SAVE_ERROR));

		Button cancelButton = DialogContentFactory.tertiaryButton(i18n, ADVERTISEMENT_DIALOG_BUTTON_CANCEL);
		cancelButton.addClickListener(event -> close());

		addActions(saveButton, cancelButton);
		super.open();
	}
}
