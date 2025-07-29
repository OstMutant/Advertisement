package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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

		VerticalLayout filtersContent = new VerticalLayout();
		filtersContent.setSpacing(true);
		filtersContent.setPadding(false);
		filtersContent.add(
			filterFields.getIdBlock(),
			filterFields.getTitleBlock(),
			filterFields.getCreatedBlock(),
			filterFields.getUpdatedBlock(),
			filterFields.getActionBlock()
		);
		Details filtersBlock = new Details("Filters", filtersContent);
		filtersBlock.setOpened(true);

		VerticalLayout sortingContent = new VerticalLayout();
		sortingContent.setSpacing(true);
		sortingContent.setPadding(false);
		sortingContent.add(
			createSortableField("ID", "id", onFilterAction),
			createSortableField("Title", "title", onFilterAction),
			createSortableField("Created At", "created_at", onFilterAction),
			createSortableField("Updated At", "updated_at", onFilterAction),
			createSortableField("User ID", "user_id", onFilterAction)
		);
		Details sortingBlock = new Details("Sorting", sortingContent);
		sortingBlock.setOpened(true);

		Button addAdvertisementButton = createAddButton(onAddButton);

		VerticalLayout collapsibleSidebar = new VerticalLayout(addAdvertisementButton, filtersBlock, sortingBlock);
		collapsibleSidebar.setSpacing(true);
		collapsibleSidebar.setPadding(false);

		add(collapsibleSidebar);
	}

	private Button createAddButton(Runnable onAddButton) {
		Button add = new Button("Add Advertisement");
		add.addClickListener(e -> onAddButton.run());
		return add;
	}

	private Component createSortableField(String label, String property, Runnable onFilterAction) {
		Span title = new Span(label);
		SortToggleButton toggle = new SortToggleButton(customSort, property, onFilterAction);
		HorizontalLayout layout = new HorizontalLayout(title, toggle);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}
}
