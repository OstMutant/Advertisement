package org.ost.advertisement.ui.views.advertisements.dialogs;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogDelegate;
import org.ost.advertisement.ui.views.components.dialogs.LabeledField;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.formatInstant;

@SpringComponent
@Scope("prototype")
@Slf4j
@AllArgsConstructor
public class AdvertisementUpsertDialog {

    private final AdvertisementService advertisementService;
    private final AdvertisementMapper mapper;
    private final LabeledField.Builder labeledFieldBuilder;
    private final I18nService i18n;
    @Getter
    private final FormDialogDelegate<AdvertisementEditDto> delegate;

    private void configureDialog() {
        AdvertisementEditDto dto = delegate.getDto();
        delegate.setTitle(
                dto.getId() == null ? i18n.get(ADVERTISEMENT_DIALOG_TITLE_NEW) : i18n.get(ADVERTISEMENT_DIALOG_TITLE_EDIT));

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
                        .withCssClasses("base-label", "gray-label")
                        .build(),
                labeledFieldBuilder.withLabel(ADVERTISEMENT_DIALOG_FIELD_UPDATED)
                        .withValue(formatInstant(dto.getUpdatedAt()))
                        .withCssClasses("base-label", "gray-label")
                        .build(),
                labeledFieldBuilder.withLabel(ADVERTISEMENT_DIALOG_FIELD_USER)
                        .withValue(String.valueOf(dto.getCreatedByUserId()))
                        .withCssClasses("base-label", "email-label")
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

    public void open() {
        delegate.open();
    }

    @SpringComponent
    @AllArgsConstructor
    public static class Builder {

        private final AdvertisementService advertisementService;
        private final AdvertisementMapper mapper;
        private final LabeledField.Builder labeledFieldBuilder;
        private final I18nService i18n;
        private final FormDialogDelegate.Builder<AdvertisementEditDto> delegateBuilder;
        private final ObjectProvider<AdvertisementUpsertDialog> dialogProvider;

        public AdvertisementUpsertDialog build(Runnable refresh) {
            return build(null, refresh);
        }

        public AdvertisementUpsertDialog build(AdvertisementInfoDto dto, Runnable refresh) {
            FormDialogDelegate<AdvertisementEditDto> delegate = delegateBuilder
                    .withClass(AdvertisementEditDto.class)
                    .withDto(dto == null ? new AdvertisementEditDto() : mapper.toAdvertisementEdit(dto))
                    .withRefresh(refresh)
                    .build();
            AdvertisementUpsertDialog dialog = dialogProvider.getObject(advertisementService, mapper,
                    labeledFieldBuilder, i18n, delegate);
            dialog.configureDialog();
            return dialog;
        }

        public AdvertisementUpsertDialog buildAndOpen(Runnable refresh) {
            return buildAndOpen(null, refresh);
        }

        public AdvertisementUpsertDialog buildAndOpen(AdvertisementInfoDto dto, Runnable refresh) {
            AdvertisementUpsertDialog dialog = build(dto, refresh);
            dialog.getDelegate().open();
            return dialog;
        }
    }
}
