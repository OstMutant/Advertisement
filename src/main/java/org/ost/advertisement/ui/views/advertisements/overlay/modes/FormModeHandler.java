package org.ost.advertisement.ui.views.advertisements.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.dto.AdvertisementEditDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogBinder;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayPrimaryButton;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayTertiaryButton;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayTextArea;
import org.ost.advertisement.ui.views.components.overlay.fields.OverlayTextField;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class FormModeHandler implements ModeHandler {

    private final AdvertisementService advertisementService;
    private final AdvertisementMapper mapper;
    private final I18nService i18n;
    private final FormDialogBinder.Builder<AdvertisementEditDto> binderBuilder;
    private final OverlayAdvertisementMetaPanel metaPanel;
    private final OverlayTextField titleField;
    private final OverlayTextArea descriptionField;
    private final OverlayPrimaryButton saveButton;
    private final OverlayTertiaryButton cancelButton;

    private Parameters params;
    private FormDialogBinder<AdvertisementEditDto> binder;

    @Value
    @lombok.Builder
    public static class Parameters {
        AdvertisementInfoDto ad;
        @NonNull Runnable onSave;
        @NonNull Runnable onCancel;
    }

    private FormModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        boolean isCreate = params.getAd() == null;

        titleField.configure(OverlayTextField.Parameters.builder()
                .labelKey(ADVERTISEMENT_OVERLAY_FIELD_TITLE)
                .placeholderKey(ADVERTISEMENT_OVERLAY_FIELD_TITLE)
                .maxLength(255)
                .required(true)
                .build());

        descriptionField.configure(OverlayTextArea.Parameters.builder()
                .labelKey(ADVERTISEMENT_OVERLAY_FIELD_DESCRIPTION)
                .placeholderKey(ADVERTISEMENT_OVERLAY_FIELD_DESCRIPTION)
                .maxLength(1000)
                .required(true)
                .build());
        descriptionField.addClassName("overlay__description-text-area");

        AdvertisementEditDto dto = isCreate
                ? new AdvertisementEditDto()
                : mapper.toAdvertisementEdit(params.getAd());
        buildBinder(dto, titleField, descriptionField);

        Div content = isCreate
                ? new Div(titleField, descriptionField)
                : new Div(titleField, descriptionField, metaPanel.configure(OverlayAdvertisementMetaPanel.Parameters.from(params.getAd())));

        saveButton.configure(OverlayPrimaryButton.Parameters.builder()
                .labelKey(ADVERTISEMENT_OVERLAY_BUTTON_SAVE)
                .build());
        cancelButton.configure(OverlayTertiaryButton.Parameters.builder()
                .labelKey(ADVERTISEMENT_OVERLAY_BUTTON_CANCEL)
                .build());

        saveButton.addClickListener(_ -> params.getOnSave().run());
        cancelButton.addClickListener(_ -> params.getOnCancel().run());

        layout.setContent(content);
        layout.setHeaderActions(new Div(saveButton, cancelButton));
    }

    public boolean save() {
        return binder.save(dto -> advertisementService.save(mapper.toAdvertisement(dto)));
    }

    private void buildBinder(AdvertisementEditDto dto,
                             OverlayTextField titleField,
                             OverlayTextArea descriptionField) {
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

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<FormModeHandler> provider;

        public FormModeHandler build(Parameters p) {
            return provider.getObject().configure(p);
        }
    }
}