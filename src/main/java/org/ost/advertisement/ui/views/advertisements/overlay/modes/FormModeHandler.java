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
import org.ost.advertisement.ui.views.advertisements.overlay.OverlayMetaHelper;
import org.ost.advertisement.ui.views.advertisements.overlay.OverlaySession;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementCancelButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementDescriptionTextArea;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementSaveButton;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementTitleTextField;
import org.ost.advertisement.ui.views.components.dialogs.FormDialogBinder;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class FormModeHandler implements ModeHandler {

    private final AdvertisementService                           advertisementService;
    private final AdvertisementMapper                            mapper;
    private final I18nService                                    i18n;
    private final FormDialogBinder.Builder<AdvertisementEditDto> binderBuilder;
    private final OverlayAdvertisementMetaPanel.Builder          metaPanelBuilder;

    private final OverlayAdvertisementTitleTextField      titleField;
    private final OverlayAdvertisementDescriptionTextArea descriptionField;
    private final OverlayAdvertisementSaveButton          saveButton;
    private final OverlayAdvertisementCancelButton        cancelButton;

    private final Div metaContainer = new Div();

    private FormDialogBinder<AdvertisementEditDto> binder;
    private OverlayLayout layout;
    private Runnable      onSave;
    private Runnable      onCancel;

    @Override
    public void configure(OverlayLayout layout, Runnable primary, Runnable secondary) {
        this.layout   = layout;
        this.onSave   = primary;
        this.onCancel = secondary;
    }

    @Override
    public void init() {
        titleField.setWidthFull();
        descriptionField.setWidthFull();
        metaContainer.addClassName("overlay__meta-container");

        layout.addContent(titleField, descriptionField, metaContainer);

        layout.addHeaderActions(saveButton, cancelButton);
        saveButton.addClickListener(_   -> onSave.run());
        cancelButton.addClickListener(_ -> onCancel.run());

        deactivate();
    }

    @Override
    public void activate(OverlaySession s) {
        boolean isCreate = s.mode() == Mode.CREATE;
        AdvertisementEditDto dto = isCreate
                ? new AdvertisementEditDto()
                : mapper.toAdvertisementEdit(s.ad());
        rebuildBinder(dto);
        if (!isCreate) OverlayMetaHelper.rebuild(metaContainer, metaPanelBuilder, s.ad());

        titleField.setVisible(true);
        descriptionField.setVisible(true);
        metaContainer.setVisible(!isCreate);
        saveButton.setVisible(true);
        cancelButton.setVisible(true);
    }

    @Override
    public void deactivate() {
        titleField.setVisible(false);
        descriptionField.setVisible(false);
        metaContainer.setVisible(false);
        saveButton.setVisible(false);
        cancelButton.setVisible(false);
    }

    public boolean save() {
        return binder.save(dto -> advertisementService.save(mapper.toAdvertisement(dto)));
    }

    private void rebuildBinder(AdvertisementEditDto dto) {
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