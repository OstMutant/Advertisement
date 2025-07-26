package org.ost.advertisement.ui.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.ost.advertisement.ui.views.sort.CustomSort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;

public class SortToggleButton extends Button {

	private Direction direction;
	private final String property;
	private final Runnable onSort;
	private final CustomSort customSort;

	public SortToggleButton(CustomSort customSort, String property, Runnable onSort) {
		this.property = property;
		this.onSort = onSort;
		this.customSort = customSort;
		this.direction = customSort.getDirection(property);
		updateIcon();
		addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		getElement().setProperty("title", "Click to sort");
		addClickListener(e -> toggleSort());
	}

	private void toggleSort() {
		direction = switch (direction) {
			case null -> Direction.ASC;
			case ASC -> Direction.DESC;
			case DESC -> null;
		};
		updateIcon();
		Order order = switch (direction) {
			case ASC -> Order.asc(property);
			case DESC -> Order.desc(property);
			case null -> null;
		};
		customSort.updateSort(property, order);
		onSort.run();
	}

	private void updateIcon() {
		setIcon(switch (direction) {
			case null -> VaadinIcon.SORT.create();
			case ASC -> VaadinIcon.ARROW_UP.create();
			case DESC -> VaadinIcon.ARROW_DOWN.create();
		});
	}
}

