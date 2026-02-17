package org.ost.advertisement.ui.views.users.query.elements.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;
import org.ost.advertisement.ui.views.components.query.elements.rows.QueryDateInlineRow;
import org.ost.advertisement.ui.views.users.query.elements.fields.UserQueryUpdatedEndDateField;
import org.ost.advertisement.ui.views.users.query.elements.fields.UserQueryUpdatedStartDateField;

import static org.ost.advertisement.constants.I18nKey.USER_SORT_UPDATED;

@SpringComponent
@UIScope
public class UserQueryUpdatedDateRow extends QueryDateInlineRow {

    public UserQueryUpdatedDateRow(I18nService i18n,
                                   SortIcon sortIcon,
                                   UserQueryUpdatedStartDateField updatedStartDate,
                                   UserQueryUpdatedEndDateField updatedEndDate) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelI18nKey(USER_SORT_UPDATED)
                .sortIcon(sortIcon)
                .startDate(updatedStartDate)
                .endDate(updatedEndDate)
                .build());
    }
}
