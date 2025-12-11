package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_CREATED_END;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_CREATED_START;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_UPDATED_END;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_UPDATED_START;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_CREATED_AT;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_TITLE;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_UPDATED_AT;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementFilterMeta;
import org.ost.advertisement.ui.views.advertisements.processor.AdvertisementFilterProcessor;
import org.ost.advertisement.ui.views.advertisements.processor.AdvertisementSortProcessor;
import org.ost.advertisement.ui.views.components.content.ContentFactory;
import org.ost.advertisement.ui.views.components.content.QueryContentFactory;
import org.ost.advertisement.ui.views.components.query.filter.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.action.QueryActionBlock;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.QueryBlockLayout;
import org.ost.advertisement.ui.views.components.query.sort.SortProcessor;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;

@SpringComponent
@UIScope
public class AdvertisementQueryBlock implements QueryBlock<AdvertisementFilterDto>, QueryBlockLayout {

	@Getter
	private final QueryActionBlock queryActionBlock;
	@Getter
	private final FilterProcessor<AdvertisementFilterDto> filterProcessor;
	@Getter
	private final SortProcessor sortProcessor;

	private final SortIcon titleSortIcon;
	private final SortIcon createdSortIcon;
	private final SortIcon updatedSortIcon;

	private final TextField titleField;
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
								   AdvertisementSortProcessor sortProcessor) {
		this.queryActionBlock = queryActionBlock;
		this.filterProcessor = filterProcessor;
		this.sortProcessor = sortProcessor;

		this.titleSortIcon = new SortIcon(i18n);
		this.createdSortIcon = new SortIcon(i18n);
		this.updatedSortIcon = new SortIcon(i18n);

		this.titleField = contentFactory.createFullTextField(ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER);
		this.createdStart = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_CREATED_START);
		this.createdEnd = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_CREATED_END);
		this.updatedStart = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_UPDATED_START);
		this.updatedEnd = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_UPDATED_END);

		this.layout = queryContentFactory.buildQueryBlockLayout(
			queryContentFactory.createQueryBlockInlineRow(ADVERTISEMENT_SORT_TITLE, titleSortIcon, titleField),
			queryContentFactory.createQueryBlockInlineRow(ADVERTISEMENT_SORT_CREATED_AT, createdSortIcon, createdStart,
				createdEnd),
			queryContentFactory.createQueryBlockInlineRow(ADVERTISEMENT_SORT_UPDATED_AT, updatedSortIcon, updatedStart,
				updatedEnd),
			this.queryActionBlock.getComponent());
	}

	@PostConstruct
	private void init() {
		sortProcessor.register(AdvertisementInfoDto.Fields.title, titleSortIcon, queryActionBlock);
		sortProcessor.register(AdvertisementInfoDto.Fields.createdAt, createdSortIcon, queryActionBlock);
		sortProcessor.register(AdvertisementInfoDto.Fields.updatedAt, updatedSortIcon, queryActionBlock);

		filterProcessor.register(AdvertisementFilterMeta.TITLE, titleField, queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_START, createdStart, queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_END, createdEnd, queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_START, updatedStart, queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_END, updatedEnd, queryActionBlock);
	}
}
