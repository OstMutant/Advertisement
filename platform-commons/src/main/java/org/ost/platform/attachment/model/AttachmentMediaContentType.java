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

    private final String value;

    public static boolean isUploadedVideo(String value) {
        return MP4.value.equals(value) || WEBM.value.equals(value);
    }

    public static boolean isEmbedded(String value) {
        return YOUTUBE.value.equals(value) || EMBED.value.equals(value);
    }

}
