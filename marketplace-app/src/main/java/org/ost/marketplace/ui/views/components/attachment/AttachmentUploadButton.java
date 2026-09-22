package org.ost.marketplace.ui.views.components.attachment;

import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import org.ost.platform.attachment.model.AttachmentAllowedContentTypes;

/** Vaadin file-picker upload control wired with the allowed attachment mime types, file-count, and size limits. */
class AttachmentUploadButton extends Upload {

    static final int  MAX_FILES     = 10;
    static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    AttachmentUploadButton(UploadHandler handler) {
        super(handler);
        addClassName("attachment-gallery__upload");
        setAcceptedMimeTypes(AttachmentAllowedContentTypes.VALUES.toArray(String[]::new));
        setMaxFiles(MAX_FILES);
        setMaxFileSize((int) MAX_FILE_SIZE);
        getElement().setAttribute("nodrop", "");
    }
}
