package org.ost.advertisement.ui.views.advertisements.overlay.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTertiaryInlineButton;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.MAIN_TAB_ADVERTISEMENTS;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class OverlayAdvertisementBreadcrumbButton extends DialogTertiaryInlineButton {

    public OverlayAdvertisementBreadcrumbButton(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(MAIN_TAB_ADVERTISEMENTS)
                .build());
        addClassName("overlay__breadcrumb-back");
    }
}