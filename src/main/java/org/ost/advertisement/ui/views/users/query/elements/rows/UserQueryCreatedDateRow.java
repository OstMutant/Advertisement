package org.ost.advertisement.ui.views.users.query.elements.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;
import org.ost.advertisement.ui.views.components.query.elements.rows.QueryDateInlineRow;
import org.ost.advertisement.ui.views.users.query.elements.fields.UserQueryCreatedEndDateField;
import org.ost.advertisement.ui.views.users.query.elements.fields.UserQueryCreatedStartDateField;

import static org.ost.advertisement.constants.I18nKey.USER_SORT_CREATED;

@SpringComponent
@UIScope
public class UserQueryCreatedDateRow extends QueryDateInlineRow {

    public UserQueryCreatedDateRow(I18nService i18n,
                                   SortIcon sortIcon,
                                   UserQueryCreatedStartDateField createdStartDate,
                                   UserQueryCreatedEndDateField createdEndDate) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelI18nKey(USER_SORT_CREATED)
                .sortIcon(sortIcon)
                .startDate(createdStartDate)
                .endDate(createdEndDate)
                .build());
    }
}
