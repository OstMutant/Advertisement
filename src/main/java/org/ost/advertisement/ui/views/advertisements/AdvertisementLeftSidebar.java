package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.List;
import lombok.Getter;

public class AdvertisementLeftSidebar extends VerticalLayout {

	@Getter
	private final AdvertisementFilterFields filterFields = new AdvertisementFilterFields();
	@Getter
	private final AdvertisementSortFields sortFields = new AdvertisementSortFields();

	public AdvertisementLeftSidebar(Runnable onRefreshAction, Runnable onAddButton) {
		setWidth("270px");
		setHeight("1000px");
		getStyle().set("box-shadow", "2px 0 4px rgba(0,0,0,0.05)");
		getStyle().set("transition", "height 0.3s ease");
		setPadding(true);
		setSpacing(true);
		getStyle().set("background-color", "#f4f4f4");

		filterFields.configure(onRefreshAction);
		List<Component> filterComponentList = List.of(
			filterFields.getIdBlock(),
			filterFields.getTitleBlock(),
			filterFields.getCreatedBlock(),
			filterFields.getUpdatedBlock(),
			filterFields.getActionBlock()
		);
		Details filtersBlock = createDetails("Filters", filterComponentList);

		sortFields.configure(onRefreshAction);
		List<Component> sortComponentList = List.of(
			sortFields.getTitleBlock(),
			sortFields.getCreatedAtBlock(),
			sortFields.getUpdatedAtBlock(),
			sortFields.getActionBlock()
		);
		Details sortingBlock = createDetails("Sorting", sortComponentList);

		Button addAdvertisementButton = createAddButton(onAddButton);

		VerticalLayout collapsibleSidebar = new VerticalLayout(addAdvertisementButton, filtersBlock, sortingBlock);
		collapsibleSidebar.setSpacing(true);
		collapsibleSidebar.setPadding(false);

		add(collapsibleSidebar);
	}

	private Details createDetails(String label, List<Component> components) {
		VerticalLayout component = new VerticalLayout();
		component.setSpacing(true);
		component.setPadding(false);
		component.add(components);
		Details sortingBlock = new Details(label, component);
		sortingBlock.setOpened(true);
		return sortingBlock;
	}

	private Button createAddButton(Runnable onAddButton) {
		Button add = new Button("Add Advertisement");
		add.addClickListener(e -> onAddButton.run());
		return add;
	}
}
