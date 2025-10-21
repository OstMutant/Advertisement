package org.ost.advertisement.ui.views.components.sort;

import com.vaadin.flow.component.html.Span;
import org.ost.advertisement.ui.utils.SvgUtils;

public class SvgSortIcon extends Span {

	public SvgSortIcon(String resourcePath) {
		String svg = SvgUtils.loadSvg(resourcePath);
		getElement().setProperty("innerHTML", svg);
		getStyle().set("width", "1em");
		getStyle().set("height", "1em");
		getStyle().set("display", "inline-block");
		getStyle().set("vertical-align", "middle");
		getStyle().set("line-height", "1");
	}
}

