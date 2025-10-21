package org.ost.advertisement.ui.views.components.sort;

import com.vaadin.flow.component.html.Span;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SvgSortIcon extends Span {

	public SvgSortIcon(String resourcePath) {
		String svg = loadSvgFromResources(resourcePath);
		getElement().setProperty("innerHTML", svg);
		getStyle().set("width", "1em");
		getStyle().set("height", "1em");
		getStyle().set("display", "inline-block");
		getStyle().set("vertical-align", "middle");
		getStyle().set("line-height", "1");
	}

	private String loadSvgFromResources(String path) {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
			if (is == null) {
				return "<!-- SVG not found -->";
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "<!-- SVG load error -->";
		}
	}
}

