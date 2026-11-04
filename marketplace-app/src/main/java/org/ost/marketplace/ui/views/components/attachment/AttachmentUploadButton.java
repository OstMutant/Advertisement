package org.ost.marketplace.ui.views.components.attachment;

import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import org.ost.platform.attachment.model.AttachmentAllowedContentTypes;
import org.ost.platform.attachment.model.AttachmentLimits;

/** Vaadin file-picker upload control wired with the allowed attachment mime types, file-count, and size limits. */
class AttachmentUploadButton extends Upload {

    AttachmentUploadButton(UploadHandler handler) {
        super(handler);
        addClassName("attachment-gallery__upload");
        setAcceptedMimeTypes(AttachmentAllowedContentTypes.VALUES.toArray(String[]::new));
        setMaxFiles(AttachmentLimits.MAX_FILES);
        setMaxFileSize((int) AttachmentLimits.MAX_FILE_SIZE);
        getElement().setAttribute("nodrop", "");
    }
}
