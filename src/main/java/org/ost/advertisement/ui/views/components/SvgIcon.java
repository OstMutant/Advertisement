package org.ost.advertisement.ui.views.components;

import com.vaadin.flow.component.html.Span;
import org.ost.advertisement.ui.utils.SvgUtils;

public class SvgIcon extends Span {

    public SvgIcon(String resourcePath) {
        addClassName("svg-icon");
        setSvg(resourcePath);
    }

    public void setSvg(String resourcePath) {
        String svg = SvgUtils.loadSvg(resourcePath);
        if (!svg.isBlank()) {
            getElement().setProperty("innerHTML", svg);
        }
    }
}


