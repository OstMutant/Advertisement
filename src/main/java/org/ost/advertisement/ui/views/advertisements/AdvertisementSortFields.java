package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SORT_CREATED_AT;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SORT_TITLE;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SORT_UPDATED_AT;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.Getter;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.ActionBlock;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.TriStateSortIcon;

@SpringComponent
@UIScope
public class AdvertisementSortFields {

	private final TriStateSortIcon titleSortIcon;
	private final TriStateSortIcon createdAtSortIcon;
	private final TriStateSortIcon updatedAtSortIcon;

	private final ActionBlock actionsBlock;

	@Getter
	private final SortFieldsProcessor sortFieldsProcessor;

	@Getter
	private final List<Component> sortComponentList;

	public AdvertisementSortFields(I18nService i18n) {
		this.sortFieldsProcessor = new SortFieldsProcessor(new CustomSort());

		this.titleSortIcon = new TriStateSortIcon();
		this.createdAtSortIcon = new TriStateSortIcon();
		this.updatedAtSortIcon = new TriStateSortIcon();
		this.actionsBlock = new ActionBlock(i18n);

		this.sortComponentList = List.of(
			createSortableField(i18n.get(ADVERTISEMENT_SORT_TITLE), titleSortIcon),
			createSortableField(i18n.get(ADVERTISEMENT_SORT_CREATED_AT), createdAtSortIcon),
			createSortableField(i18n.get(ADVERTISEMENT_SORT_UPDATED_AT), updatedAtSortIcon),
			actionsBlock.getComponent()
		);
	}

	@PostConstruct
	private void init() {
		sortFieldsProcessor.register(titleSortIcon, "title", actionsBlock);
		sortFieldsProcessor.register(createdAtSortIcon, "createdAt", actionsBlock);
		sortFieldsProcessor.register(updatedAtSortIcon, "updatedAt", actionsBlock);
	}

	public void eventProcessor(Runnable onApply) {
		Runnable combinedOnApply = () -> {
			onApply.run();
			sortFieldsProcessor.refreshSorting();
			actionsBlock.setChanged(sortFieldsProcessor.isSortingChanged());
		};
		actionsBlock.eventProcessor(() -> {
			sortFieldsProcessor.updateSorting();
			combinedOnApply.run();
		}, () -> {
			sortFieldsProcessor.clearSorting();
			combinedOnApply.run();
		});
	}

	private HorizontalLayout createSortableField(String label, Component sortComponent) {
		HorizontalLayout layout = new HorizontalLayout(new Span(label), sortComponent);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}
}
