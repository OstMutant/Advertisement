package org.ost.advertisement.ui.views.components.query.elements;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.html.Span;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.ost.advertisement.ui.views.rules.Provider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.constants.I18nKey.SORT_ICON_TOOLTIP;
import static org.ost.advertisement.ui.views.components.query.elements.SortIcon.SortIconState.fromDirection;

@Component
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class SortIcon extends Span implements Initialization<SortIcon>, I18nParams {

    @Component
    @RequiredArgsConstructor
    public static class Builder implements Provider<SortIcon> {
        @Getter
        private final ObjectProvider<SortIcon> provider;
    }

    @Getter
    private final transient I18nService i18nService;
    private final SvgIcon icon;

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

    @Override
    @PostConstruct
    public SortIcon init() {
        addClassName("sort-icon");
        setTitle(getValue(SORT_ICON_TOOLTIP));
        add(icon);
        addClickListener(_ -> cycleDirection());
        return this;
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
        icon.setTitle(getValue(state.getTooltipKey()));
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
