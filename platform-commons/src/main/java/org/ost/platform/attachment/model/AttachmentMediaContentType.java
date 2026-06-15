package org.ost.platform.attachment.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttachmentMediaContentType {
    YOUTUBE("video/youtube"),
    EMBED("video/embed"),
    MP4("video/mp4"),
    WEBM("video/webm");

    public static final String VIDEO_THUMBNAIL =
        "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E" +
        "%3Crect width='160' height='160' fill='%23222'/%3E" +
        "%3Cpolygon points='60,40 60,120 120,80' fill='white' opacity='0.7'/%3E%3C/svg%3E";

    private final String value;

    public static boolean isUploadedVideo(String value) {
        return MP4.value.equals(value) || WEBM.value.equals(value);
    }

    public static boolean isEmbedded(String value) {
        return YOUTUBE.value.equals(value) || EMBED.value.equals(value);
    }

    public static boolean isVideo(String value) {
        return isEmbedded(value) || isUploadedVideo(value);
    }

}
