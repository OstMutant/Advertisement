package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import java.util.List;
import lombok.Getter;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
public class AdvertisementLeftSidebar extends VerticalLayout {

	@Getter
	private final AdvertisementFilterFields filterFields;
	@Getter
	private final AdvertisementSortFields sortFields;
	private final Button addAdvertisementButton = new Button("Add Advertisement");


	public AdvertisementLeftSidebar(AdvertisementSortFields sortFields, AdvertisementFilterFields filterFields) {
		this.filterFields = filterFields;
		this.sortFields = sortFields;

		setWidth("270px");
		setHeight("1000px");
		getStyle().set("box-shadow", "2px 0 4px rgba(0,0,0,0.05)");
		getStyle().set("transition", "height 0.3s ease");
		setPadding(true);
		setSpacing(true);
		getStyle().set("background-color", "#f4f4f4");

		Details filtersBlock = createDetails("Filters", filterFields.getFilterComponentList());
		Details sortingBlock = createDetails("Sorting", sortFields.getSortComponentList());

		VerticalLayout collapsibleSidebar = new VerticalLayout(addAdvertisementButton, filtersBlock, sortingBlock);
		collapsibleSidebar.setSpacing(true);
		collapsibleSidebar.setPadding(false);

		add(collapsibleSidebar);
	}

	public void eventProcessor(Runnable onRefreshAction, Runnable onAddButton) {
		filterFields.eventProcessor(onRefreshAction);
		sortFields.eventProcessor(onRefreshAction);
		addAdvertisementButton.addClickListener(e -> onAddButton.run());
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
}
