package org.ost.attachment.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class YoutubeUtil {

    private static final Pattern PATTERN = Pattern.compile(
        "(?:youtube\\.com/(?:embed/|shorts/|v/|.*[?&]v=)" +
        "|youtu\\.be/)([^\"&?/\\s]{11})"
    );


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
