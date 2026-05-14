package org.ost.attachment.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.entities.MediaContentType;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.service.AttachmentService.TempAttachment;
import org.ost.attachment.util.YoutubeUtil;
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

    private static final int    MAX_FILES        = 10;
    private static final int    MAX_FILE_SIZE    = 10 * 1024 * 1024;
    private static final String CLICK_EVENT      = "click";
    private static final String STOP_PROPAGATION = "event.stopPropagation()";

    private final transient AttachmentService attachmentService;
    private final transient MessageSource     messageSource;

    private Div              thumbnailsRow;
    private Span             emptyState;
    private Upload           uploadButton;
    private HorizontalLayout videoInput;
    private boolean          editMode;

    private Long entityId;

    private final List<TempAttachment> tempUploads        = new ArrayList<>();
    private final List<Attachment>     currentAttachments = new ArrayList<>();
    private       boolean              hasPendingDeletion = false;
    private String tempSessionId;

    @PostConstruct
    private void init() {
        addClassName("attachment-gallery");

        Span title = new Span(msg("attachment.gallery.title", "Gallery"));
        title.addClassName("attachment-gallery__title");

        thumbnailsRow = new Div();
        thumbnailsRow.addClassName("attachment-gallery__thumbnails");

        emptyState = new Span(msg("attachment.gallery.empty", "Gallery is empty"));
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
        removeEditControlsIfPresent();
        refresh();
        uploadButton = buildUploadButton();
        videoInput   = buildVideoInput();
        add(uploadButton, videoInput);
    }

    public void configureForCreate(String tempSessionId) {
        this.entityId = null;
        this.editMode = true;
        this.tempSessionId = tempSessionId;
        tempUploads.clear();
        removeEditControlsIfPresent();
        thumbnailsRow.removeAll();
        showEmpty();
        uploadButton = buildUploadButton();
        videoInput   = buildVideoInput();
        add(uploadButton, videoInput);
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
            setVisible(true);
            emptyState.setVisible(false);
            currentAttachments.forEach(a -> thumbnailsRow.add(buildThumbnail(a)));
        }
    }

    private void showEmpty() {
        if (!editMode) { setVisible(false); return; }
        emptyState.setVisible(true);
    }
    private void hideEmpty() { emptyState.setVisible(false); }

    private Div buildThumbnail(Attachment attachment) {
        Div wrapper = new Div();
        wrapper.addClassName("attachment-gallery__item");

        boolean isVideo = isVideoType(attachment.getContentType());
        Image img = new Image(thumbSrc(attachment.getContentType(), attachment.getUrl()), attachment.getFilename());
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
            wrapper.add(img);
            if (isVideo) wrapper.add(videoPlayIcon());
            wrapper.add(deleteBtn);
        } else {
            img.addClickListener(_ -> openLightbox(attachment));
            wrapper.add(img);
            if (isVideo) {
                Icon play = videoPlayIcon();
                play.addClickListener(_ -> openLightbox(attachment));
                wrapper.add(play);
            }
        }
        return wrapper;
    }

    private Div buildTempThumbnail(TempAttachment temp) {
        Div wrapper = new Div();
        wrapper.addClassName("attachment-gallery__item");

        boolean isVideo = isVideoType(temp.contentType());
        Image img = new Image(thumbSrc(temp.contentType(), temp.tempUrl()), temp.filename());
        img.addClassName("attachment-gallery__image");

        Button deleteBtn = new Button(VaadinIcon.CLOSE_SMALL.create(), _ -> {
            attachmentService.discardTempUploads(List.of(temp));
            tempUploads.remove(temp);
            wrapper.removeFromParent();
            if (thumbnailsRow.getComponentCount() == 0) showEmpty();
        });
        styleDeleteBtn(deleteBtn);

        wrapper.add(img);
        if (isVideo) wrapper.add(videoPlayIcon());
        wrapper.add(deleteBtn);
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
        upload.getElement().setAttribute("nodrop", "");
        return upload;
    }

    private HorizontalLayout buildVideoInput() {
        TextField urlField = new TextField();
        urlField.setPlaceholder(msg("attachment.video.placeholder", "Video URL (YouTube, Facebook...)"));
        urlField.setWidthFull();

        Button addBtn = new Button(VaadinIcon.PLUS.create(), _ -> {
            try {
                String val = urlField.getValue();
                if (tempSessionId != null) {
                    TempAttachment temp = attachmentService.addVideoTemp(val);
                    tempUploads.add(temp);
                    hideEmpty();
                    thumbnailsRow.add(buildTempThumbnail(temp));
                } else if (entityId != null) {
                    Attachment saved = attachmentService.addVideo(entityId, val);
                    currentAttachments.add(saved);
                    hideEmpty();
                    thumbnailsRow.add(buildThumbnail(saved));
                }
                urlField.clear();
            } catch (Exception _) {
                showError(msg("attachment.video.invalid", "Invalid video URL"));
            }
        });

        HorizontalLayout row = new HorizontalLayout(urlField, addBtn);
        row.setWidthFull();
        row.setPadding(false);
        row.addClassName("attachment-gallery__video-input");
        return row;
    }

    private void removeEditControlsIfPresent() {
        if (uploadButton != null) { uploadButton.removeFromParent(); uploadButton = null; }
        if (videoInput   != null) { videoInput.removeFromParent();   videoInput   = null; }
    }

    private static void styleDeleteBtn(Button btn) {
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON);
        btn.addClassName("attachment-gallery__delete-btn");
    }

    private static Icon videoPlayIcon() {
        Icon i = VaadinIcon.PLAY_CIRCLE_O.create();
        i.addClassName("attachment-gallery__play-icon");
        return i;
    }

    private static boolean isVideoType(String contentType) {
        return MediaContentType.isVideo(contentType);
    }

    private static String thumbSrc(String contentType, String url) {
        if (MediaContentType.YOUTUBE.value().equals(contentType)) return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(url));
        if (MediaContentType.EMBED.value().equals(contentType))   return VIDEO_PLACEHOLDER_SVG;
        return url;
    }

    public static final String VIDEO_PLACEHOLDER_SVG =
        "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E" +
        "%3Crect width='160' height='160' fill='%23222'/%3E" +
        "%3Cpolygon points='60,40 60,120 120,80' fill='white' opacity='0.7'/%3E%3C/svg%3E";

    private void openLightbox(Attachment attachment) {
        Div overlay = new Div();
        overlay.addClassName("attachment-lightbox");

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), _ -> closeLightbox(overlay, null));
        closeBtn.addClassName("card-lightbox__close");
        closeBtn.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);

        if (isVideoType(attachment.getContentType())) {
            IFrame iframe = new IFrame(resolveEmbedUrl(attachment));
            iframe.addClassName("attachment-lightbox__iframe");
            iframe.getElement().setAttribute("allow",
                    "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture");
            iframe.getElement().setAttribute("allowfullscreen", "true");
            iframe.getElement().setAttribute("sandbox",
                    "allow-scripts allow-same-origin allow-presentation");
            iframe.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
            overlay.addClickListener(_ -> closeLightbox(overlay, iframe));
            overlay.add(closeBtn, iframe);
        } else {
            Image img = new Image(attachment.getUrl(), attachment.getFilename());
            img.addClassName("attachment-lightbox__image");
            img.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
            overlay.addClickListener(_ -> overlay.removeFromParent());
            overlay.add(closeBtn, img);
        }

        getUI().ifPresent(ui -> ui.getElement().appendChild(overlay.getElement()));
    }

    private static void closeLightbox(Div overlay, IFrame iframe) {
        if (iframe != null) iframe.setSrc("about:blank");
        overlay.removeFromParent();
    }

    private static String resolveEmbedUrl(Attachment attachment) {
        if (MediaContentType.YOUTUBE.value().equals(attachment.getContentType())) {
            return YoutubeUtil.embedUrl(YoutubeUtil.extractId(attachment.getUrl()));
        }
        return attachment.getUrl();
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
        } catch (Exception _) {
            return fallback;
        }
    }
}
