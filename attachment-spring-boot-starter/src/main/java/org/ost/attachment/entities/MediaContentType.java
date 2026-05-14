package org.ost.attachment.entities;

public enum MediaContentType {
    YOUTUBE("video/youtube"),
    EMBED("video/embed"),
    MP4("video/mp4"),
    WEBM("video/webm");

    private final String value;

    MediaContentType(String value) { this.value = value; }

    public String value() { return value; }

    /** True for YouTube/embed links — displayed via IFrame. */
    public static boolean isEmbedded(String contentType) {
        return YOUTUBE.value.equals(contentType) || EMBED.value.equals(contentType);
    }

    /** True for uploaded video files — displayed via &lt;video&gt; element. */
    public static boolean isUploadedVideo(String contentType) {
        return MP4.value.equals(contentType) || WEBM.value.equals(contentType);
    }

    /** True for any video (embedded or uploaded). */
    public static boolean isVideo(String contentType) {
        return isEmbedded(contentType) || isUploadedVideo(contentType);
    }

    public static final String[] ACCEPTED_VIDEO_TYPES = { MP4.value, WEBM.value };

    public static final String VIDEO_THUMBNAIL =
        "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E" +
        "%3Crect width='160' height='160' fill='%23222'/%3E" +
        "%3Cpolygon points='60,40 60,120 120,80' fill='white' opacity='0.7'/%3E%3C/svg%3E";
}
