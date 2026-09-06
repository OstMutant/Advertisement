package org.ost.sanitizer;

import lombok.experimental.UtilityClass;
import org.jsoup.Jsoup;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/** Sanitizes rich-text HTML input and enforces a caller-supplied visible-text-length cap. */
@UtilityClass
public class HtmlSanitizer {

    private static final PolicyFactory HTML_SANITIZER = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(new HtmlPolicyBuilder().allowElements("pre").toFactory());

    public static String sanitize(String html, int maxVisibleTextLength) {
        if (html == null || html.isBlank()) return html;
        String sanitized = HTML_SANITIZER.sanitize(html);
        validateVisibleTextLength(sanitized, maxVisibleTextLength);
        return sanitized;
    }

    private static void validateVisibleTextLength(String html, int maxVisibleTextLength) {
        int textLength = Jsoup.parse(html).text().length();
        if (textLength > maxVisibleTextLength) {
            throw new IllegalArgumentException(
                    "Text exceeds maximum length of " + maxVisibleTextLength + " characters");
        }
    }
}
