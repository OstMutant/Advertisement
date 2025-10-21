package org.ost.advertisement.ui.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SvgUtils {

	public static String loadSvg(String resourcePath) {
		try (InputStream is = SvgUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (is == null) {
				return "<!-- SVG not found: " + resourcePath + " -->";
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "<!-- SVG load error: " + resourcePath + " -->";
		}
	}
}
