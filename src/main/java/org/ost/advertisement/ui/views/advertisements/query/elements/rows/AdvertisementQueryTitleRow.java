package org.ost.advertisement.ui.views.advertisements.query.elements.rows;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.query.elements.fields.AdvertisementQueryTitleField;
import org.ost.advertisement.ui.views.components.query.elements.rows.QueryTextInlineRow;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_TITLE;

@SpringComponent
@UIScope
public class AdvertisementQueryTitleRow extends QueryTextInlineRow {

    public AdvertisementQueryTitleRow(I18nService i18n, SortIcon sortIcon, AdvertisementQueryTitleField titleField) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelI18nKey(ADVERTISEMENT_SORT_TITLE)
                .sortIcon(sortIcon)
                .filterField(titleField)
                .build());
    }
}
