package org.ost.advertisement.ui.views.advertisements.elements.query;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.inline.QueryDateInlineRow;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;

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
