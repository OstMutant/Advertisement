package org.ost.advertisement.ui.views.components.query.elements;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.html.Span;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.constants.I18nKey.SORT_ICON_TOOLTIP;
import static org.ost.advertisement.ui.views.components.query.elements.SortIcon.SortIconState.fromDirection;

@Component
@Scope("prototype")
public class SortIcon extends Span {

    private final transient I18nService i18n;
    private final SvgIcon icon = new SvgIcon("");

    private Direction currentDirection = null;

    public SortIcon(I18nService i18n) {
        this.i18n = i18n;
        addClassName("sort-icon");
        setTitle(i18n.get(SORT_ICON_TOOLTIP));
        add(icon);
        addClickListener(e -> cycleDirection());
        switchIcon();
    }

    public void setDirection(Direction direction) {
        this.currentDirection = direction;
        switchIcon();
    }

    private void cycleDirection() {
        setDirection(fromDirection(currentDirection).next().getDirection());
        fireEvent(new SortDirectionChangedEvent(this, currentDirection));
    }

    private void switchIcon() {
        SortIconState state = fromDirection(currentDirection);
        icon.setSvg(state.getPath());
        icon.setTitle(i18n.get(state.getTooltipKey()));
    }

    public void setColor(SortHighlightColor sortHighlightColor) {
        icon.removeClassName(SortHighlightColor.DEFAULT.getCssClass());
        icon.removeClassName(SortHighlightColor.CHANGED.getCssClass());
        icon.removeClassName(SortHighlightColor.CUSTOM.getCssClass());
        icon.addClassName(sortHighlightColor.getCssClass());
    }

    public void addDirectionChangedListener(ComponentEventListener<SortDirectionChangedEvent> listener) {
        addListener(SortDirectionChangedEvent.class, listener);
    }

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

    @Component
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<SortIcon> provider;

        public SortIcon build() {
            return provider.getObject();
        }
    }
}
