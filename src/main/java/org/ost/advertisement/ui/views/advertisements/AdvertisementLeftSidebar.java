package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;

@SpringComponent
@UIScope
public class AdvertisementLeftSidebar extends VerticalLayout {

	private final Button addAdvertisementButton = new Button("Add Advertisement");
	private final Details filtersBlock = createDetails("Filters");
	private final Details sortingBlock = createDetails("Sorting");

	@Getter
	private final transient AdvertisementFilterFields filterFields;
	@Getter
	private final transient AdvertisementSortFields sortFields;

	public AdvertisementLeftSidebar(AdvertisementFilterFields filterFields, AdvertisementSortFields sortFields) {
		this.filterFields = filterFields;
		this.sortFields = sortFields;
		filtersBlock.add(filterFields.getFilterComponentList());
		sortingBlock.add(sortFields.getSortComponentList());
	}

	public void eventProcessor(Runnable onRefreshAction, Runnable onAddButton) {
		filterFields.eventProcessor(onRefreshAction);
		sortFields.eventProcessor(onRefreshAction);
		addAdvertisementButton.addClickListener(e -> onAddButton.run());
	}

	@PostConstruct
	private void init() {
		setPadding(true);
		setSpacing(true);
		setWidth("270px");
		setHeight("1000px");
		getStyle().set("box-shadow", "2px 0 4px rgba(0,0,0,0.05)");
		getStyle().set("transition", "height 0.3s ease");
		getStyle().set("background-color", "#f4f4f4");

		VerticalLayout collapsibleSidebar = new VerticalLayout();
		collapsibleSidebar.add(addAdvertisementButton, filtersBlock, sortingBlock);
		collapsibleSidebar.setSpacing(true);
		collapsibleSidebar.setPadding(false);

		add(collapsibleSidebar);
	}

	private Details createDetails(String label) {

		VerticalLayout component = new VerticalLayout();
		component.setSpacing(true);
		component.setPadding(false);

		Details detailsBlock = new Details(label, component);
		detailsBlock.setOpened(true);

		return detailsBlock;
	}
}
