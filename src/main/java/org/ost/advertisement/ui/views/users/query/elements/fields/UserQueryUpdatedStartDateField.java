package org.ost.advertisement.ui.views.users.query.elements.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryDateTimeField;

import static org.ost.advertisement.constants.I18nKey.USER_FILTER_DATE_UPDATED_START;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_TIME_UPDATED_START;

@SpringComponent
@UIScope
@SuppressWarnings({"java:S110", "java:S2094"})
public class UserQueryUpdatedStartDateField extends QueryDateTimeField {

    public UserQueryUpdatedStartDateField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .datePlaceholderKey(USER_FILTER_DATE_UPDATED_START)
                .timePlaceholderKey(USER_FILTER_TIME_UPDATED_START)
                .build());
    }
}
