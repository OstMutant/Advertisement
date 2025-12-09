package org.ost.advertisement.ui.views.components.query.sort;

import static org.ost.advertisement.constants.I18nKey.SORT_ICON_TOOLTIP;
import static org.ost.advertisement.ui.views.components.query.sort.TriStateSortIcon.SortIconState.fromDirection;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.html.Span;
import lombok.Getter;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.SvgIcon;
import org.springframework.data.domain.Sort.Direction;

public class TriStateSortIcon extends Span {

	private final I18nService i18n;

	private Direction currentDirection = null;
	private SvgIcon currentIcon;

	public TriStateSortIcon(I18nService i18n) {
		this.i18n = i18n;
		setTitle(i18n.get(SORT_ICON_TOOLTIP));
		getStyle().set("cursor", "pointer");
		addClickListener(e -> toggleDirection());
		switchIcon();
	}

	private void toggleDirection() {
		currentDirection = fromDirection(currentDirection).next().getDirection();
		switchIcon();
		fireEvent(new SortDirectionChangedEvent(this, currentDirection));
	}

	private void switchIcon() {
		if (currentIcon != null) {
			remove(currentIcon);
		}

		SortIconState state = fromDirection(currentDirection);
		currentIcon = new SvgIcon(state.getPath());
		currentIcon.setTitle(i18n.get(state.getTooltipKey()));
		add(currentIcon);
	}

	public void setDirection(Direction direction) {
		this.currentDirection = direction;
		switchIcon();
	}

	public void setVisualColor(String color) {
		if (currentIcon != null) {
			currentIcon.getStyle().set("color", color);
		}
	}

	public void addDirectionChangedListener(ComponentEventListener<SortDirectionChangedEvent> listener) {
		addListener(SortDirectionChangedEvent.class, listener);
	}

	@Getter
	public static class SortDirectionChangedEvent extends ComponentEvent<TriStateSortIcon> {

		private final Direction direction;

		public SortDirectionChangedEvent(TriStateSortIcon source, Direction direction) {
			super(source, false);
			this.direction = direction;
		}
	}

	@Getter
	public enum SortIconState {
		NEUTRAL("icons/sort-neutral.svg", I18nKey.SORT_ICON_NEUTRAL, null),
		ASC("icons/sort-asc.svg", I18nKey.SORT_ICON_ASC, Direction.ASC),
		DESC("icons/sort-desc.svg", I18nKey.SORT_ICON_DESC, Direction.DESC);

		private final String path;
		private final I18nKey tooltipKey;
		private final Direction direction;

		SortIconState(String path, I18nKey tooltipKey, Direction direction) {
			this.path = path;
			this.tooltipKey = tooltipKey;
			this.direction = direction;
		}

		public static SortIconState fromDirection(Direction dir) {
			if (dir == null) {
				return NEUTRAL;
			}
			return switch (dir) {
				case ASC -> ASC;
				case DESC -> DESC;
			};
		}

		public SortIconState next() {
			return switch (this) {
				case NEUTRAL -> ASC;
				case ASC -> DESC;
				case DESC -> NEUTRAL;
			};
		}
	}
}
