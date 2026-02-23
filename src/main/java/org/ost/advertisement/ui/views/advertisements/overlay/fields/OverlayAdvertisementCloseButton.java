package org.ost.advertisement.ui.views.advertisements.overlay.fields;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogIconButton;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.MAIN_TAB_ADVERTISEMENTS;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class OverlayAdvertisementCloseButton extends DialogIconButton {

    public OverlayAdvertisementCloseButton(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(MAIN_TAB_ADVERTISEMENTS)
                .icon(VaadinIcon.CLOSE.create())
                .build());
    }
}