package org.ost.ui.attachment;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadEvent;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.services.AttachmentService;
import org.ost.attachment.services.AttachmentService.TempAttachment;
import org.ost.attachment.services.AttachmentSnapshotService;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.core.model.EntityType;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AttachmentGallery extends Div {

    private final transient AttachmentService         attachmentService;
    private final transient AttachmentSnapshotService attachmentSnapshotService;
    private final transient I18nService               i18n;

    private Div              thumbnailsRow;
    private Span             emptyState;
    private Upload           uploadButton;
    private HorizontalLayout videoInput;
    private boolean          editMode;

    private EntityType entityType;
    private Long       entityId;

    private final List<TempAttachment> tempUploads        = new ArrayList<>();
    private final List<Attachment>     currentAttachments = new ArrayList<>();
    private       boolean              hasPendingDeletion    = false;
    private       boolean              pendingSnapshotRestore = false;
    private String   tempSessionId;
    private transient Runnable onChanged;

    public void setOnChangedListener(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    private void notifyChanged() {
        if (onChanged != null) onChanged.run();
    }

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
        this.entityType    = entityType;
        this.entityId      = entityId;
        this.editMode      = true;
        this.tempSessionId = UUID.randomUUID().toString();
        tempUploads.clear();
        hasPendingDeletion = false;
        removeEditControlsIfPresent();
        refresh();
        uploadButton = new AttachmentUploadButton(buildUploadHandler());
        videoInput   = buildVideoInput();
        add(uploadButton, videoInput);
    }

    public void configureForCreate(@NonNull EntityType entityType, @NonNull String tempSessionId) {
        this.entityType    = entityType;
        this.entityId      = null;
        this.editMode      = true;
        this.tempSessionId = tempSessionId;
        tempUploads.clear();
        removeEditControlsIfPresent();
        thumbnailsRow.removeAll();
        showEmpty();
        uploadButton = new AttachmentUploadButton(buildUploadHandler());
        videoInput   = buildVideoInput();
        add(uploadButton, videoInput);
    }

    public void loadFromSnapshot(int version) {
        String[] urls = attachmentSnapshotService.getUrlsAtVersion(entityType, entityId, version);
        discardTempUploads();
        pendingSnapshotRestore = true;
        hasPendingDeletion = false;
        currentAttachments.clear();
        if (urls.length > 0) {
            currentAttachments.addAll(attachmentService.getByEntityAndUrls(entityType, entityId, urls));
        }
        thumbnailsRow.removeAll();
        if (currentAttachments.isEmpty()) {
            showEmpty();
        } else {
            emptyState.setVisible(false);
            setVisible(true);
            currentAttachments.forEach(a -> thumbnailsRow.add(buildThumbnail(a)));
        }
        notifyChanged();
    }

    public void commitTempUploads(@NonNull EntityType entityType, @NonNull Long entityId) {
        if (pendingSnapshotRestore) {
            String[] targetUrls = currentAttachments.stream().map(Attachment::getUrl).toArray(String[]::new);
            attachmentService.restoreToUrlsAndCapture(entityType, entityId, targetUrls);
            pendingSnapshotRestore = false;
            if (!tempUploads.isEmpty()) {
                attachmentService.commitTempUploads(entityType, entityId, tempUploads);
            }
            tempUploads.clear();
            hasPendingDeletion = false;
            return;
        }
        boolean isCreate = (this.entityId == null);
        if (tempUploads.isEmpty() && !hasPendingDeletion) return;
        if (!tempUploads.isEmpty()) {
            attachmentService.commitTempUploads(entityType, entityId, tempUploads);
        } else if (!isCreate) {
            attachmentService.captureSnapshot(entityType, entityId);
        }
        tempUploads.clear();
        hasPendingDeletion = false;
    }

    public void discardTempUploads() {
        if (pendingSnapshotRestore) {
            pendingSnapshotRestore = false;
            if (!tempUploads.isEmpty()) {
                attachmentService.discardTempUploads(tempUploads);
                tempUploads.clear();
            }
            refresh();
            return;
        }
        if (tempUploads.isEmpty()) return;
        attachmentService.discardTempUploads(tempUploads);
        tempUploads.clear();
    }

    private void refresh() {
        thumbnailsRow.removeAll();
        if (entityId == null) { showEmpty(); return; }
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

    private AttachmentThumbnail buildThumbnail(Attachment a) {
        if (editMode) {
            return AttachmentThumbnail.forEdit(a, () -> {
                attachmentService.deleteSkipSnapshot(a.getId());
                hasPendingDeletion = true;
                currentAttachments.remove(a);
                if (thumbnailsRow.getComponentCount() == 0) showEmpty();
                notifyChanged();
            });
        }
        return AttachmentThumbnail.forView(a, () -> getUI().ifPresent(ui -> AttachmentLightbox.open(a, ui)));
    }

    private AttachmentThumbnail buildTempThumbnail(TempAttachment temp) {
        return AttachmentThumbnail.forTemp(temp, () -> {
            attachmentService.discardTempUploads(List.of(temp));
            tempUploads.remove(temp);
            if (thumbnailsRow.getComponentCount() == 0) showEmpty();
            notifyChanged();
        });
    }

    private UploadHandler buildUploadHandler() {
        return new UploadHandler() {
            @Override public long getFileSizeMax()  { return AttachmentUploadButton.MAX_FILE_SIZE; }
            @Override public long getFileCountMax() { return AttachmentUploadButton.MAX_FILES; }

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
                            notifyChanged();
                        });
                    } else if (entityId != null) {
                        Attachment saved = attachmentService.upload(
                                entityType, entityId, filename, event.getInputStream(), size, contentType);
                        ui.access(() -> {
                            currentAttachments.add(saved);
                            hideEmpty();
                            thumbnailsRow.add(buildThumbnail(saved));
                            notifyChanged();
                        });
                    }
                } catch (Exception e) {
                    log.error("Failed to upload attachment: {}", filename, e);
                    ui.access(() -> showError(i18n.get(AttachmentI18n.GALLERY_UPLOAD_ERROR)));
                }
            }
        };
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
                    notifyChanged();
                } else if (entityId != null) {
                    Attachment saved = attachmentService.addVideo(entityType, entityId, val);
                    currentAttachments.add(saved);
                    hideEmpty();
                    thumbnailsRow.add(buildThumbnail(saved));
                    notifyChanged();
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

    private void showEmpty() {
        if (!editMode) { setVisible(false); return; }
        emptyState.setVisible(true);
    }

    private void hideEmpty() { emptyState.setVisible(false); }

    private static void showError(String message) {
        Notification n = Notification.show(message, 5000, Notification.Position.TOP_END);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
