package org.ost.attachment.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YoutubeUtil {

    private static final Pattern PATTERN = Pattern.compile(
        "(?:youtube\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?|shorts)/|.*[?&]v=)" +
        "|youtu\\.be/)([^\"&?/\\s]{11})"
    );

    private YoutubeUtil() {}

    public static String extractId(String url) {
        if (url == null || url.isBlank()) return null;
        Matcher m = PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static boolean isYoutube(String url) {
        return extractId(url) != null;
    }

    public static String thumbnailUrl(String videoId) {
        return "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";
    }

    public static String embedUrl(String videoId) {
        return "https://www.youtube.com/embed/" + videoId;
    }
}
