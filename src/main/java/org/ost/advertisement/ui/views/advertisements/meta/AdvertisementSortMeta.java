package org.ost.advertisement.ui.views.advertisements.meta;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.ui.views.components.query.sort.meta.SortFieldMeta;
import org.springframework.data.domain.Sort;

import static org.ost.advertisement.constants.I18nKey.*;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.createdAt;
import static org.ost.advertisement.dto.AdvertisementInfoDto.Fields.updatedAt;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementSortMeta {

    public static final SortFieldMeta TITLE = SortFieldMeta.of(Fields.title, ADVERTISEMENT_SORT_TITLE);
    public static final SortFieldMeta CREATED_AT = SortFieldMeta.of(createdAt, ADVERTISEMENT_SORT_CREATED_AT);
    public static final SortFieldMeta UPDATED_AT = SortFieldMeta.of(updatedAt, ADVERTISEMENT_SORT_UPDATED_AT);

    public static CustomSort defaultSort() {
        return new CustomSort(Sort.by(
                Sort.Order.desc(UPDATED_AT.property()),
                Sort.Order.desc(CREATED_AT.property())
        ));
    }
}
