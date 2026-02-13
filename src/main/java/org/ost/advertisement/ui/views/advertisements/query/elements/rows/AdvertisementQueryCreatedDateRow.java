package org.ost.advertisement.ui.views.advertisements.query.elements.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.query.elements.fields.AdvertisementQueryCreatedEndDatePickerField;
import org.ost.advertisement.ui.views.advertisements.query.elements.fields.AdvertisementQueryCreatedStartDatePickerField;
import org.ost.advertisement.ui.views.components.query.elements.rows.QueryDateInlineRow;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_CREATED_AT;

@SpringComponent
@UIScope
public class AdvertisementQueryCreatedDateRow extends QueryDateInlineRow {

    public AdvertisementQueryCreatedDateRow(I18nService i18n,
                                            SortIcon sortIcon,
                                            AdvertisementQueryCreatedStartDatePickerField createdStartDate,
                                            AdvertisementQueryCreatedEndDatePickerField createdEndDate) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelI18nKey(ADVERTISEMENT_SORT_CREATED_AT)
                .sortIcon(sortIcon)
                .startDate(createdStartDate)
                .endDate(createdEndDate)
                .build());
    }
}
