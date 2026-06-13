package org.ost.ui.attachment;

import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

class AttachmentUploadButton extends Upload {

    static final int  MAX_FILES     = 10;
    static final long MAX_FILE_SIZE = 500L * 1024 * 1024;

    AttachmentUploadButton(UploadHandler handler) {
        super(handler);
        addClassName("attachment-gallery__upload");
        setAcceptedFileTypes("image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm");
        setMaxFiles(MAX_FILES);
        setMaxFileSize((int) MAX_FILE_SIZE);
        getElement().setAttribute("nodrop", "");
    }
}
