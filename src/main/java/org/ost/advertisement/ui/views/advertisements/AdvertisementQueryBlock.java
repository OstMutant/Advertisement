package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.ui.views.advertisements.elements.AdvertisementQueryCreatedDateRow;
import org.ost.advertisement.ui.views.advertisements.elements.AdvertisementQueryTitleRow;
import org.ost.advertisement.ui.views.advertisements.elements.AdvertisementQueryUpdatedDateRow;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementFilterMeta;
import org.ost.advertisement.ui.views.advertisements.processor.AdvertisementFilterProcessor;
import org.ost.advertisement.ui.views.advertisements.processor.AdvertisementSortProcessor;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.QueryBlockLayout;
import org.ost.advertisement.ui.views.components.query.action.QueryActionBlock;
import org.ost.advertisement.ui.views.components.query.filter.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.sort.SortProcessor;

@SpringComponent
@UIScope
public class AdvertisementQueryBlock extends VerticalLayout implements QueryBlock<AdvertisementFilterDto>,
	QueryBlockLayout {

	@Getter
	private final QueryActionBlock queryActionBlock;
	@Getter
	private final transient FilterProcessor<AdvertisementFilterDto> filterProcessor;
	@Getter
	private final transient SortProcessor sortProcessor;

	public AdvertisementQueryBlock(AdvertisementFilterProcessor filterProcessor,
								   AdvertisementSortProcessor sortProcessor,
								   AdvertisementQueryTitleRow advertisementQueryTitleRow,
								   AdvertisementQueryCreatedDateRow advertisementQueryCreatedDateRow,
								   AdvertisementQueryUpdatedDateRow advertisementQueryUpdatedDateRow,
								   QueryActionBlock queryActionBlock) {
		this.queryActionBlock = queryActionBlock;
		this.filterProcessor = filterProcessor;
		this.sortProcessor = sortProcessor;

		initLayout(advertisementQueryTitleRow, advertisementQueryCreatedDateRow, advertisementQueryUpdatedDateRow,
			queryActionBlock);

		sortProcessor.register(AdvertisementInfoDto.Fields.title, advertisementQueryTitleRow.getSortIcon(),
			queryActionBlock);
		sortProcessor.register(AdvertisementInfoDto.Fields.createdAt, advertisementQueryCreatedDateRow.getSortIcon(),
			queryActionBlock);
		sortProcessor.register(AdvertisementInfoDto.Fields.updatedAt, advertisementQueryUpdatedDateRow.getSortIcon(),
			queryActionBlock);

		filterProcessor.register(AdvertisementFilterMeta.TITLE, advertisementQueryTitleRow.getFilterField(),
			queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_START,
			advertisementQueryCreatedDateRow.getStartDate(), queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_END, advertisementQueryCreatedDateRow.getEndDate(),
			queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_START,
			advertisementQueryUpdatedDateRow.getStartDate(), queryActionBlock);
		filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_END, advertisementQueryUpdatedDateRow.getEndDate(),
			queryActionBlock);
	}

	public void initLayout(Component... components) {
		setPadding(false);
		setSpacing(false);
		setVisible(false);
		getStyle()
			.set("margin-top", "8px")
			.set("padding", "8px")
			.set("border", "1px solid #ddd")
			.set("border-radius", "6px")
			.set("background-color", "#fafafa")
			.set("gap", "6px");
		add(components);
	}

	@Override
	public boolean toggleVisibility() {
		boolean nowVisible = !this.isVisible();
		setVisible(nowVisible);
		return nowVisible;
	}
}
