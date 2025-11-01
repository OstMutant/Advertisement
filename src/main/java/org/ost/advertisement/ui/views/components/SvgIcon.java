package org.ost.advertisement.ui.views.components;

import com.vaadin.flow.component.html.Span;
import org.ost.advertisement.ui.utils.SvgUtils;

public class SvgIcon extends Span {

	public SvgIcon(String resourcePath) {
		String svg = SvgUtils.loadSvg(resourcePath);
		getElement().setProperty("innerHTML", svg);
		getStyle().set("width", "1em");
		getStyle().set("height", "1em");
		getStyle().set("display", "inline-block");
		getStyle().set("vertical-align", "middle");
		getStyle().set("line-height", "1");
	}
}

