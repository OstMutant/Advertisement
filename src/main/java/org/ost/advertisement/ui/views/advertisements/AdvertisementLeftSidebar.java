package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
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

	public AdvertisementLeftSidebar(Runnable onFilterAction) {
		setWidth("250px");
		setHeightFull();
		setPadding(true);
		setSpacing(true);
		getStyle().set("background-color", "#f4f4f4");

		filterFields.configure(onFilterAction);

		Accordion accordion = new Accordion();

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

		AccordionPanel filtersPanel = accordion.add("Filters", filtersContent);
		filtersPanel.setOpened(true);

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
		accordion.add("Sorting", sortingContent);

		add(accordion);
	}

	private Component createSortableField(String label, String property, Runnable onFilterAction) {
		Span title = new Span(label);
		SortToggleButton toggle = new SortToggleButton(customSort, property, onFilterAction);
		HorizontalLayout layout = new HorizontalLayout(title, toggle);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}

	public void hide() {
		setVisible(false);
		setEnabled(false);
	}

	public void show() {
		setVisible(true);
		setEnabled(true);
	}

	public void toggle() {
		boolean visible = isVisible();
		setVisible(!visible);
		setEnabled(!visible);
	}
}
