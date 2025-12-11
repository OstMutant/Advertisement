package org.ost.advertisement.ui.views.advertisements.meta;

import java.util.Map;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.services.I18nService;
import org.springframework.data.domain.Sort;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementSortMeta {

	public static CustomSort defaultSort() {
		return new CustomSort(Sort.by(
			Sort.Order.desc(AdvertisementInfoDto.Fields.updatedAt),
			Sort.Order.desc(AdvertisementInfoDto.Fields.createdAt)
		));
	}

	private static final Map<String, I18nKey> labelMap = Map.of(
		AdvertisementInfoDto.Fields.title, I18nKey.ADVERTISEMENT_SORT_TITLE,
		AdvertisementInfoDto.Fields.createdAt, I18nKey.ADVERTISEMENT_SORT_CREATED_AT,
		AdvertisementInfoDto.Fields.updatedAt, I18nKey.ADVERTISEMENT_SORT_UPDATED_AT
	);

	public static UnaryOperator<String> labelProvider(I18nService i18n) {
		return property -> labelMap.containsKey(property)
			? i18n.get(labelMap.get(property))
			: property;
	}
}
