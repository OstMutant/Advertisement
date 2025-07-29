package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.List;
import lombok.Getter;
import org.ost.advertisement.ui.components.SortToggleButton;
import org.ost.advertisement.ui.views.sort.CustomSort;

public class AdvertisementLeftSidebar extends VerticalLayout {

	@Getter
	private final AdvertisementFilterFields filterFields = new AdvertisementFilterFields();
	@Getter
	private final CustomSort customSort = new CustomSort();

	public AdvertisementLeftSidebar(Runnable onFilterAction, Runnable onAddButton) {
		setWidth("250px");
		setHeight("1000px");
		getStyle().set("box-shadow", "2px 0 4px rgba(0,0,0,0.05)");
		getStyle().set("transition", "height 0.3s ease");
		setPadding(true);
		setSpacing(true);
		getStyle().set("background-color", "#f4f4f4");

		filterFields.configure(onFilterAction);

		List<Component> filterComponentList = List.of(
			filterFields.getIdBlock(),
			filterFields.getTitleBlock(),
			filterFields.getCreatedBlock(),
			filterFields.getUpdatedBlock(),
			filterFields.getActionBlock()
		);
		Details filtersBlock = createDetails("Filters", filterComponentList);

		List<Component> sortComponentList = List.of(
			createSortableField("ID", "id", onFilterAction),
			createSortableField("Title", "title", onFilterAction),
			createSortableField("Created At", "created_at", onFilterAction),
			createSortableField("Updated At", "updated_at", onFilterAction),
			createSortableField("User ID", "user_id", onFilterAction)
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

	private HorizontalLayout createSortableField(String label, String property, Runnable onFilterAction) {
		Span title = new Span(label);
		SortToggleButton toggle = new SortToggleButton(customSort, property, onFilterAction);
		HorizontalLayout layout = new HorizontalLayout(title, toggle);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}
}
