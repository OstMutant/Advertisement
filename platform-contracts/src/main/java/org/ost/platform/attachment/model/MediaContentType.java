package org.ost.platform.attachment.model;

public enum MediaContentType {
    YOUTUBE("video/youtube"),
    EMBED("video/embed"),
    MP4("video/mp4"),
    WEBM("video/webm");

    private final String value;

    MediaContentType(String value) { this.value = value; }

    public String value() { return value; }
}
