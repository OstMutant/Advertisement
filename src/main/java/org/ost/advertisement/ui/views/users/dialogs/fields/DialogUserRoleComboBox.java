package org.ost.advertisement.ui.views.users.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogComboBox;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;

import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_ROLE_LABEL;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class DialogUserRoleComboBox extends DialogComboBox<Role> {

    public DialogUserRoleComboBox(I18nService i18n) {
        super(Parameters.<Role>builder()
                .i18n(i18n)
                .labelKey(USER_DIALOG_FIELD_ROLE_LABEL)
                .items(Arrays.asList(Role.values()))
                .required(true)
                .build());
    }
}
