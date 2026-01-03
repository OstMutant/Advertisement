package org.ost.advertisement.ui.views.advertisements.elements.query;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.field.QueryTextField;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER;

@SpringComponent
@UIScope
@SuppressWarnings("java:S110")
public class AdvertisementQueryTitleField extends QueryTextField {

    public AdvertisementQueryTitleField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .placeholderKey(ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER)
                .build());
    }
}
