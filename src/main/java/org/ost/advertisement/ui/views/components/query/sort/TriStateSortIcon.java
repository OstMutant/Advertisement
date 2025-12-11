package org.ost.advertisement.ui.views.components.query.sort;

import static org.ost.advertisement.constants.I18nKey.SORT_ICON_TOOLTIP;
import static org.ost.advertisement.ui.views.components.query.sort.TriStateSortIcon.SortIconState.fromDirection;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.html.Span;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.SvgIcon;
import org.springframework.data.domain.Sort.Direction;

public class TriStateSortIcon extends Span {

	private final transient I18nService i18n;
	private final SvgIcon currentIcon = new SvgIcon(fromDirection(null).getPath());

	private Direction currentDirection = null;

	public TriStateSortIcon(I18nService i18n) {
		this.i18n = i18n;

		setTitle(i18n.get(SORT_ICON_TOOLTIP));
		getStyle().set("cursor", "pointer");
		add(currentIcon);
		addClickListener(e -> {
			setDirection(fromDirection(currentDirection).next().getDirection());
			fireEvent(new SortDirectionChangedEvent(this, currentDirection));
		});
		switchIcon();
	}

	public void setDirection(Direction direction) {
		this.currentDirection = direction;
		switchIcon();
	}

	private void switchIcon() {
		SortIconState state = fromDirection(currentDirection);
		currentIcon.loadSvg(state.getPath());
		currentIcon.setTitle(i18n.get(state.getTooltipKey()));
	}

	public void setColor(SortHighlightColor sortHighlightColor) {
		Objects.requireNonNull(sortHighlightColor);
		currentIcon.setColor(sortHighlightColor.getCssColor());
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

	@AllArgsConstructor
	@Getter
	public enum SortHighlightColor {
		DEFAULT("gray"),
		CHANGED("orange"),
		CUSTOM("green");

		private final String cssColor;
	}

	@AllArgsConstructor
	@Getter
	public enum SortIconState {
		NEUTRAL(null, "icons/sort-neutral.svg", I18nKey.SORT_ICON_NEUTRAL),
		ASC(Direction.ASC, "icons/sort-asc.svg", I18nKey.SORT_ICON_ASC),
		DESC(Direction.DESC, "icons/sort-desc.svg", I18nKey.SORT_ICON_DESC);

		private final Direction direction;
		private final String path;
		private final I18nKey tooltipKey;

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
