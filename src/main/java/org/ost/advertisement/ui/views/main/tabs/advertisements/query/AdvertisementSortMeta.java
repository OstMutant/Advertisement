package org.ost.advertisement.ui.views.main.tabs.advertisements.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.ui.views.components.query.processor.SortFieldMeta;

import static org.ost.advertisement.constants.I18nKey.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementSortMeta {

    public static final SortFieldMeta TITLE      = SortFieldMeta.of(AdvertisementInfoDto.Fields.title,     ADVERTISEMENT_SORT_TITLE);
    public static final SortFieldMeta CREATED_AT = SortFieldMeta.of(AdvertisementInfoDto.Fields.createdAt, ADVERTISEMENT_SORT_CREATED_AT);
    public static final SortFieldMeta UPDATED_AT = SortFieldMeta.of(AdvertisementInfoDto.Fields.updatedAt, ADVERTISEMENT_SORT_UPDATED_AT);
}
