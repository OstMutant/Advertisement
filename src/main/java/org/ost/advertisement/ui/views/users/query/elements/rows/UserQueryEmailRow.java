package org.ost.advertisement.ui.views.users.query.elements.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;
import org.ost.advertisement.ui.views.components.query.elements.rows.QueryTextInlineRow;
import org.ost.advertisement.ui.views.users.query.elements.fields.UserQueryEmailField;

import static org.ost.advertisement.constants.I18nKey.USER_SORT_EMAIL;

@SpringComponent
@UIScope
@Getter
public class UserQueryEmailRow extends QueryTextInlineRow {

    public UserQueryEmailRow(I18nService i18n,
                             SortIcon sortIcon,
                             UserQueryEmailField emailField) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelI18nKey(USER_SORT_EMAIL)
                .sortIcon(sortIcon)
                .filterField(emailField)
                .build());
    }
}
