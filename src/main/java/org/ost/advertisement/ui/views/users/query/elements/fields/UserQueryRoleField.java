package org.ost.advertisement.ui.views.users.query.elements.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryComboField;

import static org.ost.advertisement.constants.I18nKey.USER_FILTER_ROLE_ANY;

@SpringComponent
@UIScope
@SuppressWarnings("java:S110")
public class UserQueryRoleField extends QueryComboField<Role> {

    public UserQueryRoleField(I18nService i18n) {
        super(Parameters.<Role>builder()
                .i18n(i18n)
                .placeholderKey(USER_FILTER_ROLE_ANY)
                .items(Role.values())
                .build());
    }
}
