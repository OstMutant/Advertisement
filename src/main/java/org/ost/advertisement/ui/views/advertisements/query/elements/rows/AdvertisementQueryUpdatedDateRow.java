package org.ost.advertisement.ui.views.advertisements.query.elements.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.query.elements.fields.AdvertisementQueryUpdatedEndDatePickerField;
import org.ost.advertisement.ui.views.advertisements.query.elements.fields.AdvertisementQueryUpdatedStartDatePickerField;
import org.ost.advertisement.ui.views.components.query.elements.rows.QueryDateInlineRow;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_UPDATED_AT;

@SpringComponent
@UIScope
public class AdvertisementQueryUpdatedDateRow extends QueryDateInlineRow {

    public AdvertisementQueryUpdatedDateRow(I18nService i18n, SortIcon sortIcon,
                                            AdvertisementQueryUpdatedStartDatePickerField updatedStartDate,
                                            AdvertisementQueryUpdatedEndDatePickerField updatedEndDate) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelI18nKey(ADVERTISEMENT_SORT_UPDATED_AT)
                .sortIcon(sortIcon)
                .startDate(updatedStartDate)
                .endDate(updatedEndDate)
                .build());
    }
}
