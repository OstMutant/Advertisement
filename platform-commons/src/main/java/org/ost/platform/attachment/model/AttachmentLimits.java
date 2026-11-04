package org.ost.platform.attachment.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The gallery limits every upload path enforces: the Vaadin gallery and the REST media endpoints share these values. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttachmentLimits {

    public static final int  MAX_FILES     = 10;
    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
}
