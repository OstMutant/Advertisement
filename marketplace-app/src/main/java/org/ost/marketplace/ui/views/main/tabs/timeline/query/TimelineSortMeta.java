package org.ost.marketplace.ui.views.main.tabs.timeline.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.marketplace.ui.query.sort.SortFieldMeta;

import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_SORT_CREATED_AT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TimelineSortMeta {

    public static final SortFieldMeta CREATED_AT = SortFieldMeta.of("createdAt", TIMELINE_SORT_CREATED_AT);
}
