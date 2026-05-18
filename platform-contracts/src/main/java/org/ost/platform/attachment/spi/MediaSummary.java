package org.ost.platform.attachment.spi;

/**
 * Display-ready summary of an entity's current attachment state.
 * {@code displayUrl} is the URL ready to render (e.g. YouTube thumbnail URL for
 * YouTube content, original URL for images/uploaded video, {@code null} for
 * generic embedded video).
 */
public record MediaSummary(String displayUrl, String contentType, int count) {

    public static MediaSummary empty() {
        return new MediaSummary(null, null, 0);
    }
}
