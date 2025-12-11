package org.ost.advertisement.ui.views.components;

import com.vaadin.flow.component.html.Span;
import lombok.Getter;
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

	public void setColor(SortHighlightColor sortHighlightColor) {
		this.getStyle().set("color", sortHighlightColor.getCssColor());
	}

	@Getter
	public enum SortHighlightColor {
		DEFAULT("gray"),
		CHANGED("orange"),
		CUSTOM("green");

		private final String cssColor;

		SortHighlightColor(String cssColor) {
			this.cssColor = cssColor;
		}
	}
}

