package org.ost.advertisement.ui.views.users.elements.query.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.inline.QueryTextInlineRow;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;
import org.ost.advertisement.ui.views.users.elements.query.UserQueryNameField;

import static org.ost.advertisement.constants.I18nKey.USER_SORT_NAME;

@SpringComponent
@UIScope
@Getter
public class UserQueryNameRow extends QueryTextInlineRow {

    public UserQueryNameRow(I18nService i18n,
                            SortIcon sortIcon,
                            UserQueryNameField nameField) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelI18nKey(USER_SORT_NAME)
                .sortIcon(sortIcon)
                .filterField(nameField)
                .build());
    }
}
