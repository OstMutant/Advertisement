package org.ost.marketplace.ui.views.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jsoup.Jsoup;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HtmlExcerptUtil {

    public static String plainText(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parse(html).text();
    }
}
