package org.ost.advertisement.ui.views.advertisements.overlay.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTextField;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_OVERLAY_FIELD_TITLE;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class OverlayAdvertisementTitleTextField extends DialogTextField {

    public OverlayAdvertisementTitleTextField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(ADVERTISEMENT_OVERLAY_FIELD_TITLE)
                .placeholderKey(ADVERTISEMENT_OVERLAY_FIELD_TITLE)
                .maxLength(255)
                .required(true)
                .build());
        setWidthFull();
    }
}
