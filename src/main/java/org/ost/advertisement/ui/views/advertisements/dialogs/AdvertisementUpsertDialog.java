package org.ost.advertisement.ui.views.advertisements.dialogs;

import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.*;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogDelegate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@Slf4j
@RequiredArgsConstructor
public class AdvertisementUpsertDialog {

    private final AdvertisementService advertisementService;
    private final AdvertisementMapper mapper;
    private final I18nService i18n;

    private final DialogAdvertisementCreatedAtLabeledField createdAtField;
    private final DialogAdvertisementUpdatedAtLabeledField updatedAtField;
    private final DialogAdvertisementCreatedByLabeledField createdByField;
    private final DialogAdvertisementTitleTextField titleField;
    private final DialogAdvertisementDescriptionTextArea descriptionField;
    private final DialogAdvertisementSaveButton saveButton;
    private final DialogAdvertisementCancelButton cancelButton;

    private FormDialogDelegate<AdvertisementEditDto> delegate;

    private void configureDialog(FormDialogDelegate<AdvertisementEditDto> delegate) {
        this.delegate = delegate;
        setTitle();
        bindFields();
        updateMetadata();
        addContent();
        addActions();
    }

    private void setTitle() {
        AdvertisementEditDto dto = delegate.getDto();
        delegate.setTitle(dto.getId() == null
                ? i18n.get(ADVERTISEMENT_DIALOG_TITLE_NEW)
                : i18n.get(ADVERTISEMENT_DIALOG_TITLE_EDIT));
    }

    private void bindFields() {
        delegate.getBinder().forField(titleField)
                .asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_REQUIRED))
                .withValidator(new StringLengthValidator(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_LENGTH), 1, 255))
                .bind(AdvertisementEditDto::getTitle, AdvertisementEditDto::setTitle);

        delegate.getBinder().forField(descriptionField)
                .asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_DESCRIPTION_REQUIRED))
                .bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);
    }

    private void updateMetadata() {
        AdvertisementEditDto dto = delegate.getDto();
        createdAtField.update(dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : "");
        updatedAtField.update(dto.getUpdatedAt() != null ? dto.getUpdatedAt().toString() : "");
        createdByField.update(String.valueOf(dto.getCreatedByUserId()));
    }

    private void addContent() {
        delegate.addContent(titleField, descriptionField, createdAtField, updatedAtField, createdByField);
    }

    private void addActions() {
        saveButton.addClickListener(_ -> delegate.save(
                ad -> advertisementService.save(mapper.toAdvertisement(ad)),
                ADVERTISEMENT_DIALOG_NOTIFICATION_SUCCESS,
                ADVERTISEMENT_DIALOG_NOTIFICATION_SAVE_ERROR
        ));

        cancelButton.addClickListener(_ -> delegate.close());

        delegate.addActions(saveButton, cancelButton);
    }

    public void open() {
        delegate.open();
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {

        private final AdvertisementMapper mapper;
        private final FormDialogDelegate.Builder<AdvertisementEditDto> delegateBuilder;
        private final ObjectProvider<AdvertisementUpsertDialog> dialogProvider;

        public AdvertisementUpsertDialog build(Runnable refresh) {
            return build(null, refresh);
        }

        public AdvertisementUpsertDialog build(AdvertisementInfoDto dto, Runnable refresh) {
            FormDialogDelegate<AdvertisementEditDto> delegate = createDelegate(dto, refresh);
            AdvertisementUpsertDialog dialog = dialogProvider.getObject();
            dialog.configureDialog(delegate);
            return dialog;
        }

        public AdvertisementUpsertDialog buildAndOpen(Runnable refresh) {
            return buildAndOpen(null, refresh);
        }

        public AdvertisementUpsertDialog buildAndOpen(AdvertisementInfoDto dto, Runnable refresh) {
            AdvertisementUpsertDialog dialog = build(dto, refresh);
            dialog.open();
            return dialog;
        }

        private FormDialogDelegate<AdvertisementEditDto> createDelegate(AdvertisementInfoDto dto, Runnable refresh) {
            return delegateBuilder
                    .withClass(AdvertisementEditDto.class)
                    .withDto(dto == null ? new AdvertisementEditDto() : mapper.toAdvertisementEdit(dto))
                    .withRefresh(refresh)
                    .build();
        }
    }
}
