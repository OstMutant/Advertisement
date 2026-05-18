package org.ost.attachment.spi;

import com.vaadin.flow.component.Component;
import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentGalleryExtension;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.ui.AttachmentGallery;
import org.ost.attachment.ui.CardMediaLightbox;
import org.ost.platform.attachment.storage.ConditionalOnStorageEnabled;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class AttachmentGalleryExtensionImpl implements AttachmentGalleryExtension {

    private final ObjectProvider<AttachmentGallery>    galleryProvider;
    private final ObjectProvider<CardMediaLightbox>    lightboxProvider;
    private final AttachmentService                    attachmentService;

    @Override
    public Component buildGalleryForView(EntityType entityType, Long entityId) {
        AttachmentGallery gallery = galleryProvider.getObject();
        gallery.configureForView(entityType, entityId);
        return gallery;
    }

    @Override
    public FormHandle buildGalleryForCreate(EntityType entityType, String tempSessionId) {
        AttachmentGallery gallery = galleryProvider.getObject();
        gallery.configureForCreate(entityType, tempSessionId);
        return new Handle(gallery);
    }

    @Override
    public FormHandle buildGalleryForEdit(EntityType entityType, Long entityId) {
        AttachmentGallery gallery = galleryProvider.getObject();
        gallery.configureForEdit(entityType, entityId);
        return new Handle(gallery);
    }

    @Override
    public void openMediaLightbox(EntityType entityType, Long entityId) {
        lightboxProvider.getObject().open(attachmentService.getByEntityId(entityType, entityId), 0);
    }

    private record Handle(AttachmentGallery gallery) implements FormHandle {
        @Override public Component getComponent() {
            return gallery;
        }
        @Override public void commit(EntityType entityType, Long entityId) {
            gallery.commitTempUploads(entityType, entityId);
        }
        @Override public void discard() {
            gallery.discardTempUploads();
        }
    }
}
