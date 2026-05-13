package org.ost.attachment.entities;

public enum MediaContentType {
    YOUTUBE("video/youtube"),
    EMBED("video/embed");

    private final String value;

    MediaContentType(String value) { this.value = value; }

    public String value() { return value; }

    public static boolean isVideo(String contentType) {
        return YOUTUBE.value.equals(contentType) || EMBED.value.equals(contentType);
    }
}
