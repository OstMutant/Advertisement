package org.ost.advertisement.ui.views.components.sort;

import static org.ost.advertisement.constans.I18nKey.SORT_TOGGLE_TOOLTIP;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.services.I18nService;
import org.springframework.data.domain.Sort.Direction;

public class SortToggleButton extends Button {

	private Direction direction;
	private final String property;
	private final transient Runnable onSort;
	private final transient CustomSort customSort;

	public SortToggleButton(CustomSort customSort, String property, Runnable onSort, I18nService i18n) {
		this.property = property;
		this.onSort = onSort;
		this.customSort = customSort;
		this.direction = customSort.getDirection(property);
		updateIcon();
		addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
		getElement().setProperty("title", i18n.get(SORT_TOGGLE_TOOLTIP));
		addClickListener(e -> toggleSort());
	}

	private void toggleSort() {
		direction = switch (direction) {
			case null -> Direction.ASC;
			case ASC -> Direction.DESC;
			case DESC -> null;
		};
		updateIcon();
		customSort.updateSort(property, direction);
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


