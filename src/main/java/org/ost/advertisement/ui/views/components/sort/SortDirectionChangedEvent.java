package org.ost.advertisement.ui.views.components.sort;

import com.vaadin.flow.component.ComponentEvent;
import org.springframework.data.domain.Sort.Direction;


public class SortDirectionChangedEvent extends ComponentEvent<TriStateSortIcon> {

	private final Direction direction;

	public SortDirectionChangedEvent(TriStateSortIcon source, Direction direction) {
		super(source, false);
		this.direction = direction;
	}

	public Direction getDirection() {
		return direction;
	}
}

