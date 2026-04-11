package org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.elements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.Attachment;
import org.ost.advertisement.entities.EntityType;
import org.ost.advertisement.security.UserIdMarker;
import org.ost.advertisement.services.AttachmentService;
import org.ost.advertisement.services.AttachmentService.TempAttachment;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.storage.api.ConditionalOnStorageEnabled;
import org.springframework.context.annotation.Scope;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.ost.advertisement.constants.I18nKey.ATTACHMENT_GALLERY_EMPTY;
import static org.ost.advertisement.constants.I18nKey.ATTACHMENT_GALLERY_TITLE;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
@ConditionalOnStorageEnabled
public class AttachmentGallery extends Div implements I18nParams {

    private static final int    MAX_FILES     = 10;
    private static final int    MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final transient AttachmentService attachmentService;
    @Getter
    private final transient I18nService i18nService;

    private Div thumbnailsRow;
    private Span emptyState;
    private Upload uploadButton;
    private boolean editMode;

    private EntityType entityType;
    private Long entityId;
    private transient UserIdMarker owner;

    private final List<TempAttachment> tempUploads = new ArrayList<>();
    private String tempSessionId;

    @PostConstruct
    private void init() {
        addClassName("attachment-gallery");

        Span title = new Span(getValue(ATTACHMENT_GALLERY_TITLE));
        title.addClassName("attachment-gallery__title");

        thumbnailsRow = new Div();
        thumbnailsRow.addClassName("attachment-gallery__thumbnails");

        emptyState = new Span(getValue(ATTACHMENT_GALLERY_EMPTY));
        emptyState.addClassName("attachment-gallery__empty");
        emptyState.setVisible(false);

        add(title, thumbnailsRow, emptyState);
    }

    public void configureForView(EntityType entityType, Long entityId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.owner = null;
        this.editMode = false;
        refresh();
    }

    public void configureForEdit(EntityType entityType, Long entityId, UserIdMarker owner) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.owner = owner;
        this.editMode = true;
        this.tempSessionId = null;
        tempUploads.clear();
        removeUploadIfPresent();
        refresh();
        uploadButton = buildUploadButton();
        add(uploadButton);
    }

    public void configureForCreate(EntityType entityType, String tempSessionId) {
        this.entityType = entityType;
        this.entityId = null;
        this.owner = null;
        this.editMode = true;
        this.tempSessionId = tempSessionId;
        tempUploads.clear();
        removeUploadIfPresent();
        thumbnailsRow.removeAll();
        showEmpty();
        uploadButton = buildUploadButton();
        add(uploadButton);
    }

    public void commitTempUploads(Long entityId, UserIdMarker owner) {
        if (tempUploads.isEmpty()) return;
        attachmentService.commitTempUploads(owner, entityType, entityId, tempUploads);
        tempUploads.clear();
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
        List<Attachment> attachments = attachmentService.getByEntityId(entityType, entityId);
        if (attachments.isEmpty()) {
            showEmpty();
        } else {
            emptyState.setVisible(false);
            attachments.forEach(a -> thumbnailsRow.add(buildThumbnail(a)));
        }
    }

    private void showEmpty() {
        emptyState.setVisible(true);
    }

    private void hideEmpty() {
        emptyState.setVisible(false);
    }

    private Div buildThumbnail(Attachment attachment) {
        Div wrapper = new Div();
        wrapper.addClassName("attachment-gallery__item");

        Image img = new Image(attachment.getUrl(), attachment.getFilename());
        img.addClassName("attachment-gallery__image");

        if (editMode) {
            Button deleteBtn = new Button(VaadinIcon.CLOSE_SMALL.create(), _ -> {
                attachmentService.delete(owner, attachment.getId());
                wrapper.removeFromParent();
                if (thumbnailsRow.getComponentCount() == 0) showEmpty();
            });
            styleDeleteBtn(deleteBtn);
            wrapper.add(img, deleteBtn);
        } else {
            img.addClickListener(_ -> openLightbox(attachment.getUrl(), attachment.getFilename()));
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
                        tempSessionId,
                        metadata.fileName(),
                        new ByteArrayInputStream(bytes),
                        bytes.length,
                        metadata.contentType()
                );
                tempUploads.add(temp);
                hideEmpty();
                thumbnailsRow.add(buildTempThumbnail(temp));
            } else {
                Attachment saved = attachmentService.upload(
                        owner,
                        entityType,
                        entityId,
                        metadata.fileName(),
                        new ByteArrayInputStream(bytes),
                        bytes.length,
                        metadata.contentType()
                );
                hideEmpty();
                thumbnailsRow.add(buildThumbnail(saved));
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
        btn.addThemeVariants(
                ButtonVariant.LUMO_TERTIARY,
                ButtonVariant.LUMO_ERROR,
                ButtonVariant.LUMO_ICON);
        btn.addClassName("attachment-gallery__delete-btn");
    }

    private void openLightbox(String url, String filename) {
        Div overlay = new Div();
        overlay.addClassName("attachment-lightbox");
        overlay.addClickListener(_ -> overlay.removeFromParent());

        Image img = new Image(url, filename);
        img.addClassName("attachment-lightbox__image");
        img.addClickListener(e -> e.getSource().getElement()
                .executeJs("event.stopPropagation()"));

        overlay.add(img);
        getUI().ifPresent(ui -> ui.getElement().appendChild(overlay.getElement()));
    }
}
