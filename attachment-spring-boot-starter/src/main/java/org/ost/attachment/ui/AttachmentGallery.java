package org.ost.attachment.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entity.Attachment;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.service.AttachmentService.TempAttachment;
import org.ost.advertisement.spi.storage.ConditionalOnStorageEnabled;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@CssImport("./attachment-gallery.css")
@CssImport("./card-lightbox.css")
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
@ConditionalOnStorageEnabled
public class AttachmentGallery extends Div {

    private static final int MAX_FILES     = 10;
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final transient AttachmentService attachmentService;
    private final transient MessageSource     messageSource;

    private Div thumbnailsRow;
    private Span emptyState;
    private Upload uploadButton;
    private boolean editMode;

    private Long entityId;

    private final List<TempAttachment> tempUploads        = new ArrayList<>();
    private final List<Attachment>     currentAttachments = new ArrayList<>();
    private       boolean              hasPendingDeletion = false;
    private String tempSessionId;

    @PostConstruct
    private void init() {
        addClassName("attachment-gallery");

        Span title = new Span(msg("attachment.gallery.title", "Photos"));
        title.addClassName("attachment-gallery__title");

        thumbnailsRow = new Div();
        thumbnailsRow.addClassName("attachment-gallery__thumbnails");

        emptyState = new Span(msg("attachment.gallery.empty", "No photos"));
        emptyState.addClassName("attachment-gallery__empty");
        emptyState.setVisible(false);

        add(title, thumbnailsRow, emptyState);
    }

    public void configureForView(Long entityId) {
        this.entityId = entityId;
        this.editMode = false;
        refresh();
    }

    public void configureForEdit(Long entityId) {
        this.entityId = entityId;
        this.editMode = true;
        this.tempSessionId = UUID.randomUUID().toString();
        tempUploads.clear();
        hasPendingDeletion = false;
        removeUploadIfPresent();
        refresh();
        uploadButton = buildUploadButton();
        add(uploadButton);
    }

    public void configureForCreate(String tempSessionId) {
        this.entityId = null;
        this.editMode = true;
        this.tempSessionId = tempSessionId;
        tempUploads.clear();
        removeUploadIfPresent();
        thumbnailsRow.removeAll();
        showEmpty();
        uploadButton = buildUploadButton();
        add(uploadButton);
    }

    public void commitTempUploads(Long entityId) {
        boolean isCreate = (this.entityId == null);
        if (tempUploads.isEmpty() && !hasPendingDeletion) return;
        if (!tempUploads.isEmpty()) {
            attachmentService.commitTempUploads(entityId, tempUploads);
        } else if (hasPendingDeletion && !isCreate) {
            attachmentService.captureSnapshot(entityId);
        }
        tempUploads.clear();
        hasPendingDeletion = false;
    }

    public void discardTempUploads() {
        if (tempUploads.isEmpty()) return;
        attachmentService.discardTempUploads(tempUploads);
        tempUploads.clear();
    }

    private void refresh() {
        thumbnailsRow.removeAll();
        if (entityId == null) {
            showEmpty();
            return;
        }
        currentAttachments.clear();
        currentAttachments.addAll(attachmentService.getByEntityId(entityId));
        if (currentAttachments.isEmpty()) {
            showEmpty();
        } else {
            emptyState.setVisible(false);
            currentAttachments.forEach(a -> thumbnailsRow.add(buildThumbnail(a)));
        }
    }

    private void showEmpty() { emptyState.setVisible(true); }
    private void hideEmpty() { emptyState.setVisible(false); }

    private Div buildThumbnail(Attachment attachment) {
        Div wrapper = new Div();
        wrapper.addClassName("attachment-gallery__item");

        Image img = new Image(attachment.getUrl(), attachment.getFilename());
        img.addClassName("attachment-gallery__image");

        if (editMode) {
            Button deleteBtn = new Button(VaadinIcon.CLOSE_SMALL.create(), _ -> {
                attachmentService.deleteSkipSnapshot(attachment.getId());
                hasPendingDeletion = true;
                currentAttachments.remove(attachment);
                wrapper.removeFromParent();
                if (thumbnailsRow.getComponentCount() == 0) showEmpty();
            });
            styleDeleteBtn(deleteBtn);
            wrapper.add(img, deleteBtn);
        } else {
            img.addClickListener(_ -> openLightbox(attachment));
            wrapper.add(img);
        }
        return wrapper;
    }

    private Div buildTempThumbnail(TempAttachment temp) {
        Div wrapper = new Div();
        wrapper.addClassName("attachment-gallery__item");

        Image img = new Image(temp.tempUrl(), temp.filename());
        img.addClassName("attachment-gallery__image");

        Button deleteBtn = new Button(VaadinIcon.CLOSE_SMALL.create(), _ -> {
            attachmentService.discardTempUploads(List.of(temp));
            tempUploads.remove(temp);
            wrapper.removeFromParent();
            if (thumbnailsRow.getComponentCount() == 0) showEmpty();
        });
        styleDeleteBtn(deleteBtn);
        wrapper.add(img, deleteBtn);
        return wrapper;
    }

    private Upload buildUploadButton() {
        Upload upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            if (tempSessionId != null) {
                TempAttachment temp = attachmentService.uploadTemp(
                        tempSessionId, metadata.fileName(),
                        new ByteArrayInputStream(bytes), bytes.length, metadata.contentType()
                );
                tempUploads.add(temp);
                hideEmpty();
                thumbnailsRow.add(buildTempThumbnail(temp));
            } else {
                try {
                    Attachment saved = attachmentService.upload(
                            entityId, metadata.fileName(),
                            new ByteArrayInputStream(bytes), bytes.length, metadata.contentType()
                    );
                    hideEmpty();
                    currentAttachments.add(saved);
                    thumbnailsRow.add(buildThumbnail(saved));
                } catch (Exception e) {
                    log.error("Failed to upload attachment: {}", metadata.fileName(), e);
                    showError(msg("attachment.gallery.upload.error", "Upload failed"));
                }
            }
        }));
        upload.addClassName("attachment-gallery__upload");
        upload.setAcceptedFileTypes("image/jpeg", "image/png", "image/webp", "image/gif");
        upload.setMaxFiles(MAX_FILES);
        upload.setMaxFileSize(MAX_FILE_SIZE);
        return upload;
    }

    private void removeUploadIfPresent() {
        if (uploadButton != null) {
            uploadButton.removeFromParent();
            uploadButton = null;
        }
    }

    private static void styleDeleteBtn(Button btn) {
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON);
        btn.addClassName("attachment-gallery__delete-btn");
    }

    private void openLightbox(Attachment attachment) {
        Div overlay = new Div();
        overlay.addClassName("attachment-lightbox");
        overlay.addClickListener(_ -> overlay.removeFromParent());

        Image img = new Image(attachment.getUrl(), attachment.getFilename());
        img.addClassName("attachment-lightbox__image");
        img.getElement().addEventListener("click", _ -> {}).addEventData("event.stopPropagation()");

        overlay.add(img);
        getUI().ifPresent(ui -> ui.getElement().appendChild(overlay.getElement()));
    }

    private static void showError(String message) {
        Notification n = Notification.show(message, 5000, Notification.Position.TOP_END);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private String msg(String key, String fallback) {
        Locale locale = VaadinSession.getCurrent() != null
                ? VaadinSession.getCurrent().getLocale()
                : Locale.getDefault();
        try {
            return messageSource.getMessage(key, null, locale);
        } catch (Exception e) {
            return fallback;
        }
    }
}
