package org.ost.advertisement.ui.views.advertisements.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.views.advertisements.overlay.Mode;
import org.ost.advertisement.ui.views.advertisements.overlay.OverlaySession;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.*;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogBinder;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class FormModeHandler implements ModeHandler {

    private final AdvertisementService                                    advertisementService;
    private final AdvertisementMapper                                     mapper;
    private final I18nService                                             i18n;
    private final FormDialogBinder.Builder<AdvertisementEditDto>          binderBuilder;
    private final OverlayAdvertisementMetaPanel.Builder                   metaPanelBuilder;
    private final ObjectProvider<OverlayAdvertisementTitleTextField>      titleFieldProvider;
    private final ObjectProvider<OverlayAdvertisementDescriptionTextArea> descriptionFieldProvider;
    private final ObjectProvider<OverlayAdvertisementSaveButton>          saveButtonProvider;
    private final ObjectProvider<OverlayAdvertisementCancelButton>        cancelButtonProvider;

    private FormDialogBinder<AdvertisementEditDto> binder;
    private Runnable onSave;
    private Runnable onCancel;

    @Override
    public void setCallbacks(Runnable primary, Runnable secondary) {
        this.onSave   = primary;
        this.onCancel = secondary;
    }

    @Override
    public void activate(OverlaySession s, OverlayLayout layout) {
        boolean isCreate = s.mode() == Mode.CREATE;

        OverlayAdvertisementTitleTextField      titleField       = titleFieldProvider.getObject();
        OverlayAdvertisementDescriptionTextArea descriptionField = descriptionFieldProvider.getObject();

        AdvertisementEditDto dto = isCreate ? new AdvertisementEditDto() : mapper.toAdvertisementEdit(s.ad());
        rebuildBinder(dto, titleField, descriptionField);

        Div content;
        if (isCreate) {
            content = new Div(titleField, descriptionField);
        } else {
            Div metaContainer = metaPanelBuilder.build(s.ad());
            content = new Div(titleField, descriptionField, metaContainer);
        }

        OverlayAdvertisementSaveButton   saveButton   = saveButtonProvider.getObject();
        OverlayAdvertisementCancelButton cancelButton = cancelButtonProvider.getObject();
        saveButton.addClickListener(_   -> onSave.run());
        cancelButton.addClickListener(_ -> onCancel.run());

        layout.setContent(content);
        layout.setHeaderActions(new Div(saveButton, cancelButton));
    }

    public boolean save() {
        return binder.save(dto -> advertisementService.save(mapper.toAdvertisement(dto)));
    }

    private void rebuildBinder(AdvertisementEditDto dto,
                               OverlayAdvertisementTitleTextField titleField,
                               OverlayAdvertisementDescriptionTextArea descriptionField) {
        binder = binderBuilder.build(
                FormDialogBinder.Config.<AdvertisementEditDto>builder()
                        .clazz(AdvertisementEditDto.class)
                        .dto(dto)
                        .build()
        );
        binder.getBinder().forField(titleField)
                .asRequired(i18n.get(ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_REQUIRED))
                .withValidator(new StringLengthValidator(
                        i18n.get(ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_LENGTH), 1, 255))
                .bind(AdvertisementEditDto::getTitle, AdvertisementEditDto::setTitle);
        binder.getBinder().forField(descriptionField)
                .asRequired(i18n.get(ADVERTISEMENT_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED))
                .bind(AdvertisementEditDto::getDescription, AdvertisementEditDto::setDescription);
    }
}