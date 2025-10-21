package org.ost.advertisement.ui.views.components.sort;

import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.shared.Registration;
import lombok.Getter;
import org.ost.advertisement.ui.views.components.SvgIcon;
import org.springframework.data.domain.Sort.Direction;

public class TriStateSortIcon extends Span {

	private Direction currentDirection = null; // null = NONE
	private SvgIcon icon;

	public TriStateSortIcon() {
		setTitle("Click to change sort direction");
		getStyle().set("cursor", "pointer");
		addClickListener(e -> toggleDirection());
		updateIcon();
	}

	private void toggleDirection() {
		currentDirection = switch (currentDirection) {
			case null -> ASC;
			case ASC -> DESC;
			case DESC -> null;
		};
		updateIcon();
		fireEvent(new SortDirectionChangedEvent(this, currentDirection));
	}

	private void updateIcon() {
		if (icon != null) {
			remove(icon);
		}

		String path;
		String color;

		if (currentDirection == null) {
			path = "icons/sort-neutral.svg";
			color = "gray"; // DEFAULT
		} else {
			path = switch (currentDirection) {
				case ASC -> "icons/sort-asc.svg";
				case DESC -> "icons/sort-desc.svg";
			};
			color = "orange"; // CHANGED
		}

		icon = new SvgIcon(path);
		icon.getStyle().set("color", color);
		add(icon);
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

	public void setVisualColor(String color) {
		if (icon != null) {
			icon.getStyle().set("color", color);
		}
	}

	public Registration addDirectionChangedListener(ComponentEventListener<SortDirectionChangedEvent> listener) {
		return addListener(SortDirectionChangedEvent.class, listener);
	}

	@Getter
	public static class SortDirectionChangedEvent extends ComponentEvent<TriStateSortIcon> {

		private final Direction direction;

		public SortDirectionChangedEvent(TriStateSortIcon source, Direction direction) {
			super(source, false);
			this.direction = direction;
		}

	}
}
