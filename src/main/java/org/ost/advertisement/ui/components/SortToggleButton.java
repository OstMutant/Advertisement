package org.ost.advertisement.ui.components;

import static java.util.Optional.ofNullable;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import java.util.function.Consumer;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;

public class SortToggleButton extends Button {

	private Direction direction;
	private final String property;
	private final Consumer<Order> onSort;

	public SortToggleButton(Sort currentSort, String property, Consumer<Order> onSort) {
		this.property = property;
		this.onSort = onSort;
		this.direction = getDirection(currentSort);
		updateIcon();
		addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		getElement().setProperty("title", "Click to sort");
		addClickListener(e -> toggleSort());
	}

	private Direction getDirection(Sort currentSort) {
		return ofNullable(currentSort)
			.map(v -> v.getOrderFor(property))
			.map(Order::getDirection)
			.orElse(null);
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
		onSort.accept(order);
	}

	private void updateIcon() {
		setIcon(switch (direction) {
			case null -> VaadinIcon.SORT.create();
			case ASC -> VaadinIcon.ARROW_UP.create();
			case DESC -> VaadinIcon.ARROW_DOWN.create();
		});
	}
}

