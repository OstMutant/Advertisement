package org.ost.advertisement.ui.views.advertisements.dialogs;

import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.DialogAdvertisementCancelButton;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.DialogAdvertisementDescriptionTextArea;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.DialogAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.DialogAdvertisementSaveButton;
import org.ost.advertisement.ui.views.advertisements.dialogs.fields.DialogAdvertisementTitleTextField;
import org.ost.advertisement.ui.views.components.dialogs.BaseDialog;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AdvertisementUpsertDialog extends BaseDialog {

    private final transient AdvertisementService advertisementService;
    private final transient AdvertisementMapper mapper;
    @Getter
    private final transient I18nService i18n;

    private final DialogAdvertisementTitleTextField titleField;
    private final DialogAdvertisementDescriptionTextArea descriptionField;
    private final transient DialogAdvertisementMetaPanel.Builder metaPanelBuilder;
    private final DialogAdvertisementSaveButton saveButton;
    private final DialogAdvertisementCancelButton cancelButton;

    @Getter
    private final transient DialogLayout layout;
    @Getter
    private transient FormDialogBinder<AdvertisementEditDto> binder;

    @Override
    @PostConstruct
    protected void init() {
        super.init();
        addThemeName("advertisement-upsert");
    }

    private void configure(FormDialogBinder<AdvertisementEditDto> dialogBinder) {
        this.binder = dialogBinder;
        setTitle();
        bindFields();
        addContent();
        addActions();
    }

    private void bindFields() {
        binder.getBinder().forField(titleField)
                .asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_REQUIRED))
                .withValidator(new StringLengthValidator(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_TITLE_LENGTH), 1, 255))
                .bind(AdvertisementEditDto::getTitle, AdvertisementEditDto::setTitle);

        binder.getBinder().forField(descriptionField)
                .asRequired(i18n.get(ADVERTISEMENT_DIALOG_VALIDATION_DESCRIPTION_REQUIRED))
                .bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);
    }

    private void setTitle() {
        setHeaderTitle(isNew()
                ? i18n.get(ADVERTISEMENT_DIALOG_TITLE_NEW)
                : i18n.get(ADVERTISEMENT_DIALOG_TITLE_EDIT));
    }

    private void addContent() {
        if (isNew()) {
            layout.addFormContent(titleField, descriptionField);
        } else {
            AdvertisementEditDto dto = binder.getDto();
            layout.addFormContent(titleField, descriptionField,
                    metaPanelBuilder.build(DialogAdvertisementMetaPanel.Parameters.builder()
                            .authorName(dto.getCreatedByUserName())
                            .createdAt(dto.getCreatedAt())
                            .updatedAt(dto.getUpdatedAt())
                            .build()));
        }
    }

    private void addActions() {
        saveButton.addClickListener(_ -> savedNotifier(
                binder.save(ad -> advertisementService.save(mapper.toAdvertisement(ad))),
                ADVERTISEMENT_DIALOG_NOTIFICATION_SUCCESS,
                ADVERTISEMENT_DIALOG_NOTIFICATION_SAVE_ERROR
        ));
        cancelButton.addClickListener(_ -> close());

        getFooter().add(saveButton, cancelButton);
    }

    private boolean isNew() {
        return binder.getDto().isNew();
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {

        private final AdvertisementMapper mapper;
        private final FormDialogBinder.Builder<AdvertisementEditDto> dialogBinderBuilder;
        private final ObjectProvider<AdvertisementUpsertDialog> dialogProvider;

        public void buildAndOpen(Runnable refresh) {
            buildAndOpen(null, refresh);
        }

        public void buildAndOpen(AdvertisementInfoDto dto, Runnable refresh) {
            build(dto, refresh).open();
        }

        private AdvertisementUpsertDialog build(AdvertisementInfoDto dto, Runnable refresh) {
            AdvertisementUpsertDialog dialog = dialogProvider.getObject();
            dialog.applyRefresh(refresh);
            dialog.configure(createBinder(dto));
            return dialog;
        }

        private FormDialogBinder<AdvertisementEditDto> createBinder(AdvertisementInfoDto dto) {
            AdvertisementEditDto editDto = dto == null ? new AdvertisementEditDto() : mapper.toAdvertisementEdit(dto);
            return dialogBinderBuilder.build(FormDialogBinder.Config.<AdvertisementEditDto>builder()
                    .clazz(AdvertisementEditDto.class)
                    .dto(editDto)
                    .build());
        }
    }
}