package org.ost.advertisement.ui.views.users.elements.query.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.inline.QueryNumberInlineRow;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;
import org.ost.advertisement.ui.views.users.elements.query.UserQueryIdMinField;
import org.ost.advertisement.ui.views.users.elements.query.UserQueryIdMaxField;

import static org.ost.advertisement.constants.I18nKey.USER_SORT_ID;

@SpringComponent
@UIScope
@Getter
public class UserQueryIdRow extends QueryNumberInlineRow {

    public UserQueryIdRow(I18nService i18n,
                          SortIcon sortIcon,
                          UserQueryIdMinField minField,
                          UserQueryIdMaxField maxField) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelI18nKey(USER_SORT_ID)
                .sortIcon(sortIcon)
                .minField(minField)
                .maxField(maxField)
                .build());
    }
}
