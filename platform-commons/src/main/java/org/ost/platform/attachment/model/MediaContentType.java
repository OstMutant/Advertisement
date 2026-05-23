package org.ost.platform.attachment.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MediaContentType {
    YOUTUBE("video/youtube"),
    EMBED("video/embed"),
    MP4("video/mp4"),
    WEBM("video/webm");

    private final String value;
}
