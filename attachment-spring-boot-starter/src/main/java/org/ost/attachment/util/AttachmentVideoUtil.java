package org.ost.attachment.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.platform.attachment.util.YoutubeUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttachmentVideoUtil {

    private static final Set<String> ALLOWED_EMBED_HOSTS = Set.of("vimeo.com", "player.vimeo.com");

    public record VideoDescriptor(String url, String filename, String contentType) {}

    public static VideoDescriptor resolveVideoDescriptor(String url) {
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) {
            return new VideoDescriptor(YoutubeUtil.watchUrl(ytId), YoutubeUtil.filename(ytId),
                    AttachmentMediaContentType.YOUTUBE.getValue());
        }
        if (url.isBlank()) throw new IllegalArgumentException("Invalid video URL");
        validateEmbedUrl(url);
        return new VideoDescriptor(url, embedFilename(url), AttachmentMediaContentType.EMBED.getValue());
    }

    public static String resolveDisplayUrl(String url, String contentType) {
        if (url == null) return null;
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(contentType)) {
            return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(url));
        }
        if (AttachmentMediaContentType.EMBED.getValue().equals(contentType)) return null;
        return url;
    }

    private static String embedFilename(String url) {
        return url.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void validateEmbedUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid video URL", e);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean validScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        boolean validHost = host != null && ALLOWED_EMBED_HOSTS.stream()
                .anyMatch(allowed -> host.equalsIgnoreCase(allowed) || host.toLowerCase().endsWith("." + allowed));
        if (!validScheme || !validHost) throw new IllegalArgumentException("Invalid video URL");
    }
}
