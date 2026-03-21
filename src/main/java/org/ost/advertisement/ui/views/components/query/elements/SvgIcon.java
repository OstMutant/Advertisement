package org.ost.advertisement.ui.views.components.query.elements;

import com.vaadin.flow.component.html.Span;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.ost.advertisement.ui.views.utils.SvgUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class SvgIcon extends Span implements Configurable<SvgIcon, SvgIcon.Parameters>, Initialization<SvgIcon> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull String resourcePath;
    }

    @Component
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<SvgIcon, Parameters> {
        @Getter
        private final ObjectProvider<SvgIcon> provider;
    }

    @Override
    @PostConstruct
    public SvgIcon init() {
        addClassName("svg-icon");
        return this;
    }

    @Override
    public SvgIcon configure(Parameters p) {
        setSvg(p.getResourcePath());
        return this;
    }

    public void setSvg(String resourcePath) {
        String svg = SvgUtils.loadSvg(resourcePath);
        if (!svg.isBlank()) {
            getElement().setProperty("innerHTML", svg);
        }
    }
}
