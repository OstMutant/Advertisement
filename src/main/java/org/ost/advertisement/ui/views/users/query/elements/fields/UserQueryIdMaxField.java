package org.ost.advertisement.ui.views.users.query.elements.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryNumberField;

import static org.ost.advertisement.constants.I18nKey.USER_FILTER_ID_MAX;

@SpringComponent
@UIScope
public class UserQueryIdMaxField extends QueryNumberField {

    public UserQueryIdMaxField(I18nService i18n) {
        super(Parameters.builder()
                .i18nService(i18n)
                .placeholderKey(USER_FILTER_ID_MAX)
                .build());
    }
}
