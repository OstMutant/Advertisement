package org.ost.attachment.util;

import lombok.NoArgsConstructor;
import org.ost.platform.attachment.model.AttachmentMediaContentType;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class MediaContentTypeUtil {

    public static final String VIDEO_THUMBNAIL =
        "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E" +
        "%3Crect width='160' height='160' fill='%23222'/%3E" +
        "%3Cpolygon points='60,40 60,120 120,80' fill='white' opacity='0.7'/%3E%3C/svg%3E";

    public static boolean isEmbedded(String contentType) {
        return AttachmentMediaContentType.YOUTUBE.getValue().equals(contentType)
            || AttachmentMediaContentType.EMBED.getValue().equals(contentType);
    }

    public static boolean isUploadedVideo(String contentType) {
        return AttachmentMediaContentType.MP4.getValue().equals(contentType)
            || AttachmentMediaContentType.WEBM.getValue().equals(contentType);
    }

    public static boolean isVideo(String contentType) {
        return isEmbedded(contentType) || isUploadedVideo(contentType);
    }
}
