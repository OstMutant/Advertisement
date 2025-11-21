package org.ost.advertisement.ui.views.advertisements.dialogs;

import static org.ost.advertisement.constants.I18nKey.*;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;
import static org.ost.advertisement.ui.views.TailwindStyle.EMAIL_LABEL;
import static org.ost.advertisement.ui.views.TailwindStyle.GRAY_LABEL;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogDelegate;
import org.ost.advertisement.ui.views.components.dialogs.LabeledField;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@Slf4j
@AllArgsConstructor
public class AdvertisementUpsertDialog {

	private final AdvertisementService advertisementService;
	private final AdvertisementMapper mapper;
	private final FormDialogDelegate.Builder<AdvertisementEditDto> delegateBuilder;
	private final LabeledField.Builder labeledFieldBuilder;
	private final I18nService i18n;

	public void openNew(Runnable refresh) {
		AdvertisementEditDto dto = new AdvertisementEditDto();
		FormDialogDelegate<AdvertisementEditDto> delegate = buildDelegate(dto, refresh);
		delegate.setTitle(i18n.get(ADVERTISEMENT_DIALOG_TITLE_NEW));
		configureDialog(delegate, dto);
		delegate.open();
	}

	public void openEdit(AdvertisementInfoDto dto, Runnable refresh) {
		AdvertisementEditDto editDto = mapper.toAdvertisementEdit(Objects.requireNonNull(dto));
		FormDialogDelegate<AdvertisementEditDto> delegate = buildDelegate(editDto, refresh);
		delegate.setTitle(i18n.get(ADVERTISEMENT_DIALOG_TITLE_EDIT));
		configureDialog(delegate, editDto);
		delegate.open();
	}

	private FormDialogDelegate<AdvertisementEditDto> buildDelegate(AdvertisementEditDto dto, Runnable refresh) {
		return delegateBuilder
			.withClass(AdvertisementEditDto.class)
			.withDto(dto)
			.withRefresh(refresh)
			.build();
	}

	private void configureDialog(FormDialogDelegate<AdvertisementEditDto> delegate, AdvertisementEditDto dto) {
		TextField titleField = DialogContentFactory.textField(
			i18n, ADVERTISEMENT_DIALOG_FIELD_TITLE, ADVERTISEMENT_DIALOG_FIELD_TITLE, 255, true
		);

		TextArea descriptionField = DialogContentFactory.textArea(
			i18n, ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION, ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION, 1000, true
		);

		delegate.getBinder().forField(titleField)
			.asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_REQUIRED))
			.withValidator(new StringLengthValidator(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_LENGTH), 1, 255))
			.bind(AdvertisementEditDto::getTitle, AdvertisementEditDto::setTitle);

		delegate.getBinder().forField(descriptionField)
			.asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_DESCRIPTION_REQUIRED))
			.bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);

		delegate.addContent(
			titleField,
			descriptionField,
			labeledFieldBuilder.withLabel(ADVERTISEMENT_DIALOG_FIELD_CREATED)
				.withValue(formatInstant(dto.getCreatedAt()))
				.withStyles(GRAY_LABEL)
				.build(),
			labeledFieldBuilder.withLabel(ADVERTISEMENT_DIALOG_FIELD_UPDATED)
				.withValue(formatInstant(dto.getUpdatedAt()))
				.withStyles(GRAY_LABEL)
				.build(),
			labeledFieldBuilder.withLabel(ADVERTISEMENT_DIALOG_FIELD_USER)
				.withValue(String.valueOf(dto.getCreatedByUserId()))
				.withStyles(EMAIL_LABEL)
				.build()
		);

		Button saveButton = DialogContentFactory.primaryButton(i18n, ADVERTISEMENT_DIALOG_BUTTON_SAVE);
		saveButton.addClickListener(event -> delegate.save(
			ad -> advertisementService.save(mapper.toAdvertisement(ad)),
			ADVERTISEMENT_DIALOG_NOTIFICATION_SUCCESS,
			ADVERTISEMENT_DIALOG_NOTIFICATION_SAVE_ERROR
		));

		Button cancelButton = DialogContentFactory.tertiaryButton(i18n, ADVERTISEMENT_DIALOG_BUTTON_CANCEL);
		cancelButton.addClickListener(event -> delegate.close());

		delegate.addActions(saveButton, cancelButton);
	}
}
