package org.ost.attachment.spi;

import com.vaadin.flow.component.Component;
import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentGalleryPort;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.ui.AttachmentGallery;
import org.ost.attachment.ui.CardMediaLightbox;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;

@RequiredArgsConstructor
public class AttachmentGalleryPortImpl implements AttachmentGalleryPort {

    private final ObjectProvider<AttachmentGallery>    galleryProvider;
    private final ObjectProvider<CardMediaLightbox>    lightboxProvider;
    private final AttachmentService                    attachmentService;

    @Override
    public Component buildGalleryForView(EntityRef entity) {
        AttachmentGallery gallery = galleryProvider.getObject();
        gallery.configureForView(entity.entityType(), entity.entityId());
        return gallery;
    }

    @Override
    public FormHandle buildGalleryForCreate(EntityType entityType, String tempSessionId) {
        AttachmentGallery gallery = galleryProvider.getObject();
        gallery.configureForCreate(entityType, tempSessionId);
        return new Handle(gallery);
    }

    @Override
    public FormHandle buildGalleryForEdit(EntityRef entity) {
        AttachmentGallery gallery = galleryProvider.getObject();
        gallery.configureForEdit(entity.entityType(), entity.entityId());
        return new Handle(gallery);
    }

    @Override
    public void openMediaLightbox(EntityRef entity) {
        lightboxProvider.getObject().open(attachmentService.getByEntityId(entity.entityType(), entity.entityId()), 0);
    }

    private record Handle(AttachmentGallery gallery) implements FormHandle {
        @Override public Component getComponent() {
            return gallery;
        }
        @Override public void commit(EntityRef entity) {
            gallery.commitTempUploads(entity.entityType(), entity.entityId());
        }
        @Override public void discard() {
            gallery.discardTempUploads();
        }
    }
}
