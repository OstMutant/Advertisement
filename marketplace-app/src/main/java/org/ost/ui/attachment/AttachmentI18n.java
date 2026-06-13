package org.ost.ui.attachment;

import lombok.RequiredArgsConstructor;
import org.ost.platform.core.i18n.TranslationKey;

@RequiredArgsConstructor
public enum AttachmentI18n implements TranslationKey {
    GALLERY_TITLE("attachment.gallery.title"),
    GALLERY_EMPTY("attachment.gallery.empty"),
    GALLERY_UPLOAD_ERROR("attachment.gallery.upload.error"),
    VIDEO_PLACEHOLDER("attachment.video.placeholder"),
    VIDEO_INVALID("attachment.video.invalid");

    private final String key;

    @Override
    public String key() { return key; }
}
