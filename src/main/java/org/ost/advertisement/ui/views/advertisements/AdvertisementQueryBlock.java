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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.mappers.filters.AdvertisementFilterMapper;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementFilterMeta;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.components.ActionBlock;
import org.ost.advertisement.ui.views.components.ContentFactory;
import org.ost.advertisement.ui.views.components.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.TriStateSortIcon;

@SpringComponent
@UIScope
public class AdvertisementQueryBlock {

	private final ActionBlock actionsBlock;
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

	private final I18nService i18n;

	@Getter
	private final VerticalLayout layout;

	public AdvertisementQueryBlock(I18nService i18n,
								   AdvertisementFilterMapper filterMapper,
								   ValidationService<AdvertisementFilterDto> validation) {
		this.i18n = i18n;
		this.actionsBlock = new ActionBlock(i18n);
		this.filterProcessor = new FilterFieldsProcessor<>(filterMapper, validation, AdvertisementFilterDto.empty());
		this.sortProcessor = new SortFieldsProcessor(AdvertisementSortMeta.defaultSort());

		this.titleSortIcon = new TriStateSortIcon();
		this.createdSortIcon = new TriStateSortIcon();
		this.updatedSortIcon = new TriStateSortIcon();

		this.titleField = ContentFactory.createFullTextField(i18n.get(ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER));
		this.createdStart = ContentFactory.createDatePicker(i18n.get(ADVERTISEMENT_FILTER_CREATED_START));
		this.createdEnd = ContentFactory.createDatePicker(i18n.get(ADVERTISEMENT_FILTER_CREATED_END));
		this.updatedStart = ContentFactory.createDatePicker(i18n.get(ADVERTISEMENT_FILTER_UPDATED_START));
		this.updatedEnd = ContentFactory.createDatePicker(i18n.get(ADVERTISEMENT_FILTER_UPDATED_END));

		this.layout = buildLayout();
	}

	@PostConstruct
	private void init() {
		sortProcessor.register(titleSortIcon, AdvertisementInfoDto.Fields.title, actionsBlock);
		sortProcessor.register(createdSortIcon, AdvertisementInfoDto.Fields.createdAt, actionsBlock);
		sortProcessor.register(updatedSortIcon, AdvertisementInfoDto.Fields.updatedAt, actionsBlock);
		sortProcessor.refreshSorting();

		filterProcessor.register(titleField, AdvertisementFilterMeta.TITLE, actionsBlock);
		filterProcessor.register(createdStart, AdvertisementFilterMeta.CREATED_AT_START, actionsBlock);
		filterProcessor.register(createdEnd, AdvertisementFilterMeta.CREATED_AT_END, actionsBlock);
		filterProcessor.register(updatedStart, AdvertisementFilterMeta.UPDATED_AT_START, actionsBlock);
		filterProcessor.register(updatedEnd, AdvertisementFilterMeta.UPDATED_AT_END, actionsBlock);
	}

	private VerticalLayout buildLayout() {
		VerticalLayout layout = new VerticalLayout();
		layout.setPadding(false);
		layout.setSpacing(false);
		layout.setVisible(false);
		layout.getStyle()
			.set("margin-top", "8px")
			.set("padding", "8px")
			.set("border", "1px solid #ddd")
			.set("border-radius", "6px")
			.set("background-color", "#fafafa")
			.set("gap", "6px");

		layout.add(createRow(i18n.get(ADVERTISEMENT_SORT_TITLE), titleSortIcon, titleField));
		layout.add(createRow(i18n.get(ADVERTISEMENT_SORT_CREATED_AT), createdSortIcon, createdStart, createdEnd));
		layout.add(createRow(i18n.get(ADVERTISEMENT_SORT_UPDATED_AT), updatedSortIcon, updatedStart, updatedEnd));
		layout.add(actionsBlock.getComponent());

		return layout;
	}

	private Component createRow(String labelText, Component sortIcon, Component... filterFields) {
		HorizontalLayout labelAndSort = new HorizontalLayout(new Span(labelText), sortIcon);
		labelAndSort.setAlignItems(Alignment.CENTER);
		labelAndSort.setSpacing(true);

		VerticalLayout filters = new VerticalLayout(filterFields);
		filters.setPadding(false);
		filters.setSpacing(false);
		filters.getStyle().set("gap", "4px");

		VerticalLayout block = new VerticalLayout(labelAndSort, filters);
		block.setPadding(false);
		block.setSpacing(false);
		return block;
	}

	public void eventProcessor(Runnable onApply) {
		Runnable combined = () -> {
			onApply.run();
			filterProcessor.refreshFilter();
			sortProcessor.refreshSorting();
			actionsBlock.setChanged(filterProcessor.isFilterChanged() || sortProcessor.isSortingChanged());
		};

		actionsBlock.eventProcessor(() -> {
			if (!filterProcessor.validate()) {
				return;
			}
			filterProcessor.updateFilter();
			sortProcessor.updateSorting();
			combined.run();
		}, () -> {
			filterProcessor.clearFilter();
			sortProcessor.clearSorting();
			combined.run();
		});
	}
}
