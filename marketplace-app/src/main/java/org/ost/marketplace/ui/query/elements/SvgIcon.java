package org.ost.marketplace.ui.query.elements;

import com.vaadin.flow.component.html.Span;
import lombok.NonNull;
import org.ost.marketplace.ui.query.utils.SvgUtil;

public class SvgIcon extends Span {

    public SvgIcon(@NonNull String resourcePath) {
        addClassName("svg-icon");
        setSvg(resourcePath);
    }

    public void setSvg(@NonNull String resourcePath) {
        String svg = SvgUtil.loadSvg(resourcePath);
        if (!svg.isBlank()) {
            getElement().setProperty("innerHTML", svg);
        }
    }
}
