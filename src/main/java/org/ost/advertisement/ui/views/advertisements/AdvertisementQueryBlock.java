package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_CREATED_END;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_CREATED_START;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_UPDATED_END;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_UPDATED_START;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_CREATED_AT;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_UPDATED_AT;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.elements.AdvertisementQueryTitleRow;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementFilterMeta;
import org.ost.advertisement.ui.views.advertisements.processor.AdvertisementFilterProcessor;
import org.ost.advertisement.ui.views.advertisements.processor.AdvertisementSortProcessor;
import org.ost.advertisement.ui.views.components.content.ContentFactory;
import org.ost.advertisement.ui.views.components.content.QueryContentFactory;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.QueryBlockLayout;
import org.ost.advertisement.ui.views.components.query.action.QueryActionBlock;
import org.ost.advertisement.ui.views.components.query.filter.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;
import org.ost.advertisement.ui.views.components.query.sort.SortProcessor;

@SpringComponent
@UIScope
public class AdvertisementQueryBlock implements QueryBlock<AdvertisementFilterDto>, QueryBlockLayout {

	@Getter
	private final QueryActionBlock queryActionBlock;
	@Getter
	private final FilterProcessor<AdvertisementFilterDto> filterProcessor;
	@Getter
	private final SortProcessor sortProcessor;

	private final SortIcon createdSortIcon;
	private final SortIcon updatedSortIcon;

	private final DatePicker createdStart;
	private final DatePicker createdEnd;
	private final DatePicker updatedStart;
	private final DatePicker updatedEnd;

	@Getter
	private final Component layout;

	public AdvertisementQueryBlock(I18nService i18n, AdvertisementFilterProcessor filterProcessor,
								   ContentFactory contentFactory,
								   QueryContentFactory queryContentFactory,
								   QueryActionBlock queryActionBlock,
								   AdvertisementSortProcessor sortProcessor,
								   AdvertisementQueryTitleRow advertisementQueryTitleRow) {
		this.queryActionBlock = queryActionBlock;
		this.filterProcessor = filterProcessor;
		this.sortProcessor = sortProcessor;

		this.createdSortIcon = new SortIcon(i18n);
		this.updatedSortIcon = new SortIcon(i18n);

		this.createdStart = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_CREATED_START);
		this.createdEnd = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_CREATED_END);
		this.updatedStart = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_UPDATED_START);
		this.updatedEnd = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_UPDATED_END);

		this.layout = queryContentFactory.buildQueryBlockLayout(
			advertisementQueryTitleRow,
			queryContentFactory.createQueryBlockInlineRow(ADVERTISEMENT_SORT_CREATED_AT, createdSortIcon, createdStart,
				createdEnd),
			queryContentFactory.createQueryBlockInlineRow(ADVERTISEMENT_SORT_UPDATED_AT, updatedSortIcon, updatedStart,
				updatedEnd),
			this.queryActionBlock.getComponent());

		sortProcessor.register(AdvertisementInfoDto.Fields.title, advertisementQueryTitleRow.getSortIcon(),
			queryActionBlock);
		sortProcessor.register(AdvertisementInfoDto.Fields.createdAt, createdSortIcon, queryActionBlock);
		sortProcessor.register(AdvertisementInfoDto.Fields.updatedAt, updatedSortIcon, queryActionBlock);

		filterProcessor.register(AdvertisementFilterMeta.TITLE, advertisementQueryTitleRow.getFilterField(),
			queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_START, createdStart, queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_END, createdEnd, queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_START, updatedStart, queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_END, updatedEnd, queryActionBlock);
	}
}
