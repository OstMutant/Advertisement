package org.ost.advertisement.ui.views.advertisements.overlay.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogPrimaryButton;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_OVERLAY_BUTTON_SAVE;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class OverlayAdvertisementSaveButton extends DialogPrimaryButton {

    public OverlayAdvertisementSaveButton(I18nService i18n) {
        super(Parameters.builder()
                .i18nService(i18n)
                .labelKey(ADVERTISEMENT_OVERLAY_BUTTON_SAVE)
                .build());
    }
}
