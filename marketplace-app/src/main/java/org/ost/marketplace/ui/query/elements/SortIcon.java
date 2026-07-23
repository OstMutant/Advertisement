package org.ost.marketplace.ui.query.elements;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.html.Span;
import lombok.AllArgsConstructor;
import lombok.Getter;

import org.ost.marketplace.services.i18n.I18nService;

import org.springframework.data.domain.Sort.Direction;

import java.util.Arrays;

import static java.util.Optional.ofNullable;
import static org.ost.marketplace.services.i18n.I18nKey.SORT_ICON_TOOLTIP;

import org.ost.marketplace.services.i18n.I18nKey;

public class SortIcon extends Span {

    @Getter
    private final transient I18nService i18nService;
    private final SvgIcon icon = new SvgIcon(SortIconState.NEUTRAL.getPath());

    private Direction currentDirection;

    @Getter
    public static class SortDirectionChangedEvent extends ComponentEvent<SortIcon> {
        private final Direction direction;

        public SortDirectionChangedEvent(SortIcon source, Direction direction) {
            super(source, false);
            this.direction = direction;
        }
    }

    @AllArgsConstructor
    @Getter
    public enum SortHighlightColor {
        DEFAULT("default"),
        CHANGED("changed"),
        CUSTOM("custom");
        private final String cssClass;
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
            return ofNullable(dir).map(v -> v == Direction.ASC ? ASC : DESC).orElse(NEUTRAL);
        }

        public SortIconState next() {
            return switch (this) {
                case NEUTRAL -> ASC;
                case ASC -> DESC;
                case DESC -> NEUTRAL;
            };
        }
    }

    public SortIcon(I18nService i18nService) {
        this.i18nService = i18nService;
        addClassName("sort-icon");
        setTitle(i18nService.get(SORT_ICON_TOOLTIP));
        getElement().setAttribute("role", "button");
        getElement().setAttribute("aria-label", i18nService.get(SORT_ICON_TOOLTIP));
        add(icon);
        addClickListener(_ -> cycleDirection());
    }

    public void setDirection(Direction direction) {
        this.currentDirection = direction;
        switchIcon();
    }

    private void cycleDirection() {
        setDirection(SortIconState.fromDirection(currentDirection).next().getDirection());
        fireEvent(new SortDirectionChangedEvent(this, currentDirection));
    }

    private void switchIcon() {
        SortIconState state = SortIconState.fromDirection(currentDirection);
        icon.setSvg(state.getPath());
        icon.setTitle(i18nService.get(state.getTooltipKey()));
        getElement().setAttribute("aria-label", i18nService.get(state.getTooltipKey()));
    }

    public void setColor(SortHighlightColor sortHighlightColor) {
        icon.removeClassNames(Arrays.stream(SortHighlightColor.values())
                .map(SortHighlightColor::getCssClass).toArray(String[]::new));
        icon.addClassName(sortHighlightColor.getCssClass());
    }

    public void addDirectionChangedListener(ComponentEventListener<SortDirectionChangedEvent> listener) {
        addListener(SortDirectionChangedEvent.class, listener);
    }
}
