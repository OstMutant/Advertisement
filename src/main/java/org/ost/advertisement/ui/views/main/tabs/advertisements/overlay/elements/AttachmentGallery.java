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
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.entities.AdvertisementAttachment;
import org.ost.advertisement.services.AttachmentService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.List;

import static org.ost.advertisement.constants.I18nKey.ATTACHMENT_GALLERY_EMPTY;
import static org.ost.advertisement.constants.I18nKey.ATTACHMENT_GALLERY_TITLE;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AttachmentGallery extends Div implements I18nParams {

    private final transient AttachmentService attachmentService;
    @Getter
    private final transient I18nService       i18nService;

    private Div              thumbnailsRow;
    private Span             emptyState;
    private Upload           uploadButton;
    private boolean          editMode;
    private transient AdvertisementInfoDto ad;

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

    /** View mode — read-only thumbnails; click opens lightbox. */
    public void configureForView(AdvertisementInfoDto ad) {
        this.ad       = ad;
        this.editMode = false;
        refresh();
    }

    /** Edit / create mode — thumbnails with delete overlay + upload button. */
    public void configureForEdit(AdvertisementInfoDto ad) {
        this.ad       = ad;
        this.editMode = true;
        removeUploadIfPresent();
        refresh();
        uploadButton = buildUploadButton();
        add(uploadButton);
    }

    // ---------------------------------------------------------------- private

    private void refresh() {
        thumbnailsRow.removeAll();
        if (ad == null) {
            showEmpty();
            return;
        }
        List<AdvertisementAttachment> attachments =
                attachmentService.getByAdvertisementId(ad.getId());
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

    private Div buildThumbnail(AdvertisementAttachment attachment) {
        Div wrapper = new Div();
        wrapper.addClassName("attachment-gallery__item");

        Image img = new Image(attachment.getUrl(), attachment.getFilename());
        img.addClassName("attachment-gallery__image");

        if (editMode) {
            Button deleteBtn = new Button(VaadinIcon.CLOSE_SMALL.create(), _ -> {
                attachmentService.delete(ad, attachment.getId());
                wrapper.removeFromParent();
                if (thumbnailsRow.getComponentCount() == 0) {
                    showEmpty();
                }
            });
            deleteBtn.addThemeVariants(
                    ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_ERROR,
                    ButtonVariant.LUMO_ICON);
            deleteBtn.addClassName("attachment-gallery__delete-btn");
            wrapper.add(img, deleteBtn);
        } else {
            img.addClickListener(_ -> openLightbox(attachment.getUrl(), attachment.getFilename()));
            wrapper.add(img);
        }

        return wrapper;
    }

    private Upload buildUploadButton() {
        Upload upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            if (ad == null) return; // upload blocked until ad is saved (handled by caller)
            AdvertisementAttachment saved = attachmentService.upload(
                    ad,
                    ad.getId(),
                    metadata.fileName(),
                    new java.io.ByteArrayInputStream(bytes),
                    bytes.length,
                    metadata.contentType()
            );
            hideEmpty();
            thumbnailsRow.add(buildThumbnail(saved));
        }));
        upload.addClassName("attachment-gallery__upload");
        upload.setAcceptedFileTypes("image/jpeg", "image/png", "image/webp", "image/gif");
        upload.setMaxFiles(10);
        upload.setMaxFileSize(10 * 1024 * 1024); // 10 MB
        return upload;
    }

    private void removeUploadIfPresent() {
        if (uploadButton != null) {
            uploadButton.removeFromParent();
            uploadButton = null;
        }
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