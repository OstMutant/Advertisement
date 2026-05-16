package org.ost.attachment.ui;

import org.ost.advertisement.core.i18n.TranslationKey;

public enum AttachmentMessages implements TranslationKey {
    GALLERY_TITLE("attachment.gallery.title"),
    GALLERY_EMPTY("attachment.gallery.empty"),
    GALLERY_UPLOAD_ERROR("attachment.gallery.upload.error"),
    VIDEO_PLACEHOLDER("attachment.video.placeholder"),
    VIDEO_INVALID("attachment.video.invalid");

    private final String key;

    AttachmentMessages(String key) { this.key = key; }

    @Override
    public String key() { return key; }
}
