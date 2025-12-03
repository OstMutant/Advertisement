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
import org.ost.advertisement.mappers.filters.AdvertisementFilterMapper;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementFilterMeta;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.components.content.ContentFactory;
import org.ost.advertisement.ui.views.components.content.QueryBlockFactory;
import org.ost.advertisement.ui.views.components.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.query.QueryActionBlock;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.QueryBlockLayout;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.TriStateSortIcon;

@SpringComponent
@UIScope
public class AdvertisementQueryBlock implements QueryBlock<AdvertisementFilterDto>, QueryBlockLayout {

	@Getter
	private final QueryActionBlock queryActionBlock;
	@Getter
	private final FilterFieldsProcessor<AdvertisementFilterDto> filterProcessor;
	@Getter
	private final SortFieldsProcessor sortProcessor;

	private final TriStateSortIcon titleSortIcon;
	private final TriStateSortIcon createdSortIcon;
	private final TriStateSortIcon updatedSortIcon;

	private final TextField titleField;
	private final DatePicker createdStart;
	private final DatePicker createdEnd;
	private final DatePicker updatedStart;
	private final DatePicker updatedEnd;

	@Getter
	private final Component layout;

	public AdvertisementQueryBlock(AdvertisementFilterMapper filterMapper,
								   ValidationService<AdvertisementFilterDto> validation,
								   ContentFactory contentFactory,
								   QueryBlockFactory queryBlockFactory,
								   QueryActionBlock queryActionBlock) {
		this.queryActionBlock = queryActionBlock;
		this.filterProcessor = new FilterFieldsProcessor<>(filterMapper, validation, AdvertisementFilterDto.empty());
		this.sortProcessor = new SortFieldsProcessor(AdvertisementSortMeta.defaultSort());

		this.titleSortIcon = new TriStateSortIcon();
		this.createdSortIcon = new TriStateSortIcon();
		this.updatedSortIcon = new TriStateSortIcon();

		this.titleField = contentFactory.createFullTextField(ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER);
		this.createdStart = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_CREATED_START);
		this.createdEnd = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_CREATED_END);
		this.updatedStart = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_UPDATED_START);
		this.updatedEnd = contentFactory.createDatePicker(ADVERTISEMENT_FILTER_UPDATED_END);

		this.layout = queryBlockFactory.buildQueryBlockLayout(
			queryBlockFactory.createQueryBlockInlineRow(ADVERTISEMENT_SORT_TITLE, titleSortIcon, titleField),
			queryBlockFactory.createQueryBlockInlineRow(ADVERTISEMENT_SORT_CREATED_AT, createdSortIcon, createdStart,
				createdEnd),
			queryBlockFactory.createQueryBlockInlineRow(ADVERTISEMENT_SORT_UPDATED_AT, updatedSortIcon, updatedStart,
				updatedEnd),
			this.queryActionBlock.getComponent());
	}

	@PostConstruct
	private void init() {
		sortProcessor.register(titleSortIcon, AdvertisementInfoDto.Fields.title, queryActionBlock);
		sortProcessor.register(createdSortIcon, AdvertisementInfoDto.Fields.createdAt, queryActionBlock);
		sortProcessor.register(updatedSortIcon, AdvertisementInfoDto.Fields.updatedAt, queryActionBlock);
		sortProcessor.refreshSorting();

		filterProcessor.register(titleField, AdvertisementFilterMeta.TITLE, queryActionBlock);
		filterProcessor.register(createdStart, AdvertisementFilterMeta.CREATED_AT_START, queryActionBlock);
		filterProcessor.register(createdEnd, AdvertisementFilterMeta.CREATED_AT_END, queryActionBlock);
		filterProcessor.register(updatedStart, AdvertisementFilterMeta.UPDATED_AT_START, queryActionBlock);
		filterProcessor.register(updatedEnd, AdvertisementFilterMeta.UPDATED_AT_END, queryActionBlock);
	}
}
