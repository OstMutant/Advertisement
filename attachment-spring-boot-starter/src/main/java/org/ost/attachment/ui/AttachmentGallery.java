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
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entities.Attachment;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.attachment.services.AttachmentService;
import org.ost.attachment.util.MediaContentTypeUtil;
import org.ost.attachment.services.AttachmentService.TempAttachment;
import org.ost.attachment.util.YoutubeUtil;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.EntityType;
import org.springframework.context.annotation.Scope;

import com.vaadin.flow.server.streams.UploadEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@CssImport("./attachment-gallery.css")
@CssImport("./card-lightbox.css")
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AttachmentGallery extends Div {

    private static final int  MAX_FILES     = 10;
    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;
    private static final String CLICK_EVENT      = "click";
    private static final String STOP_PROPAGATION = "event.stopPropagation()";

    private final transient AttachmentService attachmentService;
    private final transient I18nService       i18n;

    private Div              thumbnailsRow;
    private Span             emptyState;
    private Upload           uploadButton;
    private HorizontalLayout videoInput;
    private boolean          editMode;

    private EntityType entityType;
    private Long       entityId;

    private final List<TempAttachment> tempUploads        = new ArrayList<>();
    private final List<Attachment>     currentAttachments = new ArrayList<>();
    private       boolean              hasPendingDeletion = false;
    private String tempSessionId;

    @PostConstruct
    private void init() {
        addClassName("attachment-gallery");

        Span title = new Span(i18n.get(AttachmentI18n.GALLERY_TITLE));
        title.addClassName("attachment-gallery__title");

        thumbnailsRow = new Div();
        thumbnailsRow.addClassName("attachment-gallery__thumbnails");

        emptyState = new Span(i18n.get(AttachmentI18n.GALLERY_EMPTY));
        emptyState.addClassName("attachment-gallery__empty");
        emptyState.setVisible(false);

        add(title, thumbnailsRow, emptyState);
    }

    public void configureForView(@NonNull EntityType entityType, @NonNull Long entityId) {
        this.entityType = entityType;
        this.entityId   = entityId;
        this.editMode   = false;
        refresh();
    }

    public void configureForEdit(@NonNull EntityType entityType, @NonNull Long entityId) {
        this.entityType = entityType;
        this.entityId   = entityId;
        this.editMode   = true;
        this.tempSessionId = UUID.randomUUID().toString();
        tempUploads.clear();
        hasPendingDeletion = false;
        removeEditControlsIfPresent();
        refresh();
        uploadButton = buildUploadButton();
        videoInput   = buildVideoInput();
        add(uploadButton, videoInput);
    }

    public void configureForCreate(@NonNull EntityType entityType, @NonNull String tempSessionId) {
        this.entityType = entityType;
        this.entityId   = null;
        this.editMode   = true;
        this.tempSessionId = tempSessionId;
        tempUploads.clear();
        removeEditControlsIfPresent();
        thumbnailsRow.removeAll();
        showEmpty();
        uploadButton = buildUploadButton();
        videoInput   = buildVideoInput();
        add(uploadButton, videoInput);
    }

    public void commitTempUploads(@NonNull EntityType entityType, @NonNull Long entityId) {
        boolean isCreate = (this.entityId == null);
        if (tempUploads.isEmpty() && !hasPendingDeletion) return;
        if (!tempUploads.isEmpty()) {
            attachmentService.commitTempUploads(entityType, entityId, tempUploads);
        } else if (hasPendingDeletion && !isCreate) {
            attachmentService.captureSnapshot(entityType, entityId);
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
        currentAttachments.addAll(attachmentService.getByEntityId(entityType, entityId));
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
        UploadHandler handler = new UploadHandler() {
            @Override public long getFileSizeMax()   { return MAX_FILE_SIZE; }
            @Override public long getFileCountMax()  { return MAX_FILES; }

            @Override
            public void handleUploadRequest(UploadEvent event) {
                String filename    = event.getFileName();
                String contentType = event.getContentType();
                long   size        = event.getFileSize();
                var    ui          = event.getUI();
                try {
                    if (tempSessionId != null) {
                        TempAttachment temp = attachmentService.uploadTemp(
                                tempSessionId, filename, event.getInputStream(), size, contentType);
                        ui.access(() -> {
                            tempUploads.add(temp);
                            hideEmpty();
                            thumbnailsRow.add(buildTempThumbnail(temp));
                        });
                    } else if (entityId != null) {
                        Attachment saved = attachmentService.upload(
                                entityType, entityId, filename, event.getInputStream(), size, contentType);
                        ui.access(() -> {
                            currentAttachments.add(saved);
                            hideEmpty();
                            thumbnailsRow.add(buildThumbnail(saved));
                        });
                    }
                } catch (Exception e) {
                    log.error("Failed to upload attachment: {}", filename, e);
                    ui.access(() -> showError(i18n.get(AttachmentI18n.GALLERY_UPLOAD_ERROR)));
                }
            }
        };

        Upload upload = new Upload(handler);
        upload.addClassName("attachment-gallery__upload");
        upload.setAcceptedFileTypes("image/jpeg", "image/png", "image/webp", "image/gif",
                                    "video/mp4", "video/webm");
        upload.setMaxFiles(MAX_FILES);
        upload.setMaxFileSize((int) MAX_FILE_SIZE);
        upload.getElement().setAttribute("nodrop", "");
        return upload;
    }

    private HorizontalLayout buildVideoInput() {
        TextField urlField = new TextField();
        urlField.setPlaceholder(i18n.get(AttachmentI18n.VIDEO_PLACEHOLDER));
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
                    Attachment saved = attachmentService.addVideo(entityType, entityId, val);
                    currentAttachments.add(saved);
                    hideEmpty();
                    thumbnailsRow.add(buildThumbnail(saved));
                }
                urlField.clear();
            } catch (Exception _) {
                showError(i18n.get(AttachmentI18n.VIDEO_INVALID));
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
        return MediaContentTypeUtil.isVideo(contentType);
    }

    private static String thumbSrc(String contentType, String url) {
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(contentType))  return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(url));
        if (MediaContentTypeUtil.isEmbedded(contentType))             return VIDEO_PLACEHOLDER_SVG;
        if (MediaContentTypeUtil.isUploadedVideo(contentType))        return VIDEO_PLACEHOLDER_SVG;
        return url;
    }

    public static final String VIDEO_PLACEHOLDER_SVG = MediaContentTypeUtil.VIDEO_THUMBNAIL;

    private void openLightbox(Attachment attachment) {
        Div overlay = new Div();
        overlay.addClassName("attachment-lightbox");

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), _ -> closeLightbox(overlay, null));
        closeBtn.addClassName("card-lightbox__close");
        closeBtn.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);

        String ct = attachment.getContentType();
        if (MediaContentTypeUtil.isEmbedded(ct)) {
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
        } else if (MediaContentTypeUtil.isUploadedVideo(ct)) {
            com.vaadin.flow.dom.Element videoEl = new com.vaadin.flow.dom.Element("video");
            videoEl.setAttribute("controls", "");
            videoEl.setAttribute("src", attachment.getUrl());
            videoEl.getClassList().add("attachment-lightbox__video");
            Div videoWrapper = new Div();
            videoWrapper.getElement().appendChild(videoEl);
            videoWrapper.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
            overlay.addClickListener(_ -> {
                videoEl.executeJs("this.pause(); this.src='';");
                overlay.removeFromParent();
            });
            overlay.add(closeBtn, videoWrapper);
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
        if (AttachmentMediaContentType.YOUTUBE.getValue().equals(attachment.getContentType())) {
            return YoutubeUtil.embedUrl(YoutubeUtil.extractId(attachment.getUrl()));
        }
        return attachment.getUrl();
    }

    private static void showError(String message) {
        Notification n = Notification.show(message, 5000, Notification.Position.TOP_END);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
