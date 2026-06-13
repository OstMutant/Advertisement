package org.ost.ui.query.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SvgUtil {

    public static String loadSvg(String resourcePath) {
        try (InputStream is = SvgUtil.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return "<!-- SVG not found: " + resourcePath + " -->";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException _) {
            return "<!-- SVG load error: " + resourcePath + " -->";
        }
    }
}
