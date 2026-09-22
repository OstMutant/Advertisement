package org.ost.platform.attachment.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

/** The single whitelist of content types an attachment upload may declare or actually contain, shared by the client-side upload picker and the server-side upload validator. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttachmentAllowedContentTypes {

    public static final Set<String> VALUES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            AttachmentMediaContentType.MP4.getValue(), AttachmentMediaContentType.WEBM.getValue());
}
