package org.ost.query.ui.elements;

import com.vaadin.flow.component.html.Span;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.Initialization;
import org.ost.query.ui.utils.SvgUtil;
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

    @Override
    @PostConstruct
    public SvgIcon init() {
        addClassName("svg-icon");
        return this;
    }

    @Override
    public SvgIcon configure(@NonNull Parameters p) {
        setSvg(p.getResourcePath());
        return this;
    }

    public void setSvg(@NonNull String resourcePath) {
        String svg = SvgUtil.loadSvg(resourcePath);
        if (!svg.isBlank()) {
            getElement().setProperty("innerHTML", svg);
        }
    }
}
