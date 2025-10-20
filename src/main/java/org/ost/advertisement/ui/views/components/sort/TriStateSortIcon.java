package org.ost.advertisement.ui.views.components.sort;

import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.shared.Registration;
import org.springframework.data.domain.Sort.Direction;

public class TriStateSortIcon extends Span {

	private Direction currentDirection = null; // null = NONE
	private final Icon icon;

	public TriStateSortIcon() {
		this.icon = new Icon(VaadinIcon.SORT);
		add(icon);
		setClassName("tri-sort-icon");
		setTitle("Click to change sort direction");
		getStyle().set("cursor", "pointer");

		addClickListener(e -> toggleDirection());
		updateIcon();
	}

	private void toggleDirection() {
		currentDirection = switch (currentDirection) {
			case null -> Direction.ASC;
			case ASC -> Direction.DESC;
			case DESC -> null;
		};
		updateIcon();
		fireEvent(new SortDirectionChangedEvent(this, currentDirection));
	}

	private void updateIcon() {
		icon.setIcon(currentDirection == null
			? VaadinIcon.SORT
			: currentDirection == Direction.ASC
				? VaadinIcon.ARROW_UP
				: VaadinIcon.ARROW_DOWN);
	}

	public Direction getDirection() {
		return currentDirection;
	}

	public void setDirection(Direction direction) {
		this.currentDirection = direction;
		updateIcon();
	}

	public void clear() {
		setDirection(null);
	}

	public Registration addDirectionChangedListener(ComponentEventListener<SortDirectionChangedEvent> listener) {
		return addListener(SortDirectionChangedEvent.class, listener);
	}

	public void setVisualColor(String color) {
		icon.getStyle().set("color", color);
	}
}
