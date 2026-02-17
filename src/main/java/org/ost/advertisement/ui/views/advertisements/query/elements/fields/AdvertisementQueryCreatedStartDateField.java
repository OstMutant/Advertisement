package org.ost.advertisement.ui.views.advertisements.query.elements.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryDateTimeField;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_DATE_CREATED_START;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_TIME_CREATED_START;

@SpringComponent
@UIScope
@SuppressWarnings({"java:S110", "java:S2094"})
public class AdvertisementQueryCreatedStartDateField extends QueryDateTimeField {

    public AdvertisementQueryCreatedStartDateField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .datePlaceholderKey(ADVERTISEMENT_FILTER_DATE_CREATED_START)
                .timePlaceholderKey(ADVERTISEMENT_FILTER_TIME_CREATED_START)
                .build());
    }
}
