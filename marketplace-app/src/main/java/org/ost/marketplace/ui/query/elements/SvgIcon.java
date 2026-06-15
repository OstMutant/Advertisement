package org.ost.marketplace.ui.query.elements;

import com.vaadin.flow.component.html.Span;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.Initialization;
import org.ost.marketplace.ui.query.utils.SvgUtil;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.springframework.context.annotation.Scope;

@SpringComponent
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
