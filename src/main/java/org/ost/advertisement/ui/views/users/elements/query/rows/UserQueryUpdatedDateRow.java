package org.ost.advertisement.ui.views.users.elements.query.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.inline.QueryDateInlineRow;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;
import org.ost.advertisement.ui.views.users.elements.query.UserQueryUpdatedStartDatePickerField;
import org.ost.advertisement.ui.views.users.elements.query.UserQueryUpdatedEndDatePickerField;

import static org.ost.advertisement.constants.I18nKey.USER_SORT_UPDATED;

@SpringComponent
@UIScope
public class UserQueryUpdatedDateRow extends QueryDateInlineRow {

    public UserQueryUpdatedDateRow(I18nService i18n,
                                   SortIcon sortIcon,
                                   UserQueryUpdatedStartDatePickerField updatedStartDate,
                                   UserQueryUpdatedEndDatePickerField updatedEndDate) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelI18nKey(USER_SORT_UPDATED)
                .sortIcon(sortIcon)
                .startDate(updatedStartDate)
                .endDate(updatedEndDate)
                .build());
    }
}
