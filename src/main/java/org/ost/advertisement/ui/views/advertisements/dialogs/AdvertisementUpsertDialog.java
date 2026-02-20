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
import org.ost.advertisement.ui.views.advertisements.AdvertisementMetaFactory;
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

    private final DialogAdvertisementTitleTextField titleField;
    private final DialogAdvertisementDescriptionTextArea descriptionField;
    private final DialogAdvertisementSaveButton saveButton;
    private final DialogAdvertisementCancelButton cancelButton;

    private FormDialogDelegate<AdvertisementEditDto> delegate;

    private void configureDialog(FormDialogDelegate<AdvertisementEditDto> delegate) {
        this.delegate = delegate;
        delegate.addDialogThemeName("advertisement-upsert");
        setTitle();
        bindFields();
        addContent();
        addActions();
    }

    private void setTitle() {
        delegate.setTitle(isNew()
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

    private void addContent() {
        if (isNew()) {
            delegate.addContent(titleField, descriptionField);
        } else {
            AdvertisementEditDto dto = delegate.getDto();
            delegate.addContent(
                    titleField,
                    descriptionField,
                    AdvertisementMetaFactory.create(i18n, dto.getCreatedByUserName(), dto.getCreatedAt(), dto.getUpdatedAt())
            );
        }
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

    private boolean isNew() {
        return delegate.getDto().getId() == null;
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