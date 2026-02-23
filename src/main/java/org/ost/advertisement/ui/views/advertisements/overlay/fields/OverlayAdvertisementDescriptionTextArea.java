package org.ost.advertisement.ui.views.advertisements.overlay.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTextArea;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_OVERLAY_FIELD_DESCRIPTION;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class OverlayAdvertisementDescriptionTextArea extends DialogTextArea {

    public OverlayAdvertisementDescriptionTextArea(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(ADVERTISEMENT_OVERLAY_FIELD_DESCRIPTION)
                .placeholderKey(ADVERTISEMENT_OVERLAY_FIELD_DESCRIPTION)
                .maxLength(1000)
                .required(true)
                .build());
        setWidthFull();
        setMinHeight("12em");
    }
}