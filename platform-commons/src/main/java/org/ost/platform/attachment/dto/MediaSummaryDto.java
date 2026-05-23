package org.ost.platform.attachment.dto;

/**
 * Display-ready summary of an entity's current attachment state.
 * {@code displayUrl} is the URL ready to render (e.g. YouTube thumbnail URL for
 * YouTube content, original URL for images/uploaded video, {@code null} for
 * generic embedded video).
 */
public record MediaSummaryDto(String displayUrl, String contentType, int count) {

    public static MediaSummaryDto empty() {
        return new MediaSummaryDto(null, null, 0);
    }
}
