package org.ost.attachment.spi;

import com.vaadin.flow.component.Component;
import lombok.RequiredArgsConstructor;
import org.ost.attachment.entities.Attachment;
import org.ost.platform.attachment.spi.AttachmentGalleryPort;
import org.ost.attachment.services.AttachmentService;
import org.ost.attachment.ui.AttachmentGallery;
import org.ost.attachment.ui.CardMediaLightbox;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.ui.ComponentFactory;
import com.vaadin.flow.spring.annotation.SpringComponent;

import java.util.List;

@SpringComponent
@RequiredArgsConstructor
public class AttachmentGalleryPortImpl implements AttachmentGalleryPort {

    private final ComponentFactory<AttachmentGallery>   galleryFactory;
    private final ComponentFactory<CardMediaLightbox>   lightboxFactory;
    private final AttachmentService                     attachmentService;

    @Override
    public Component buildGalleryForView(EntityRef entity) {
        AttachmentGallery gallery = galleryFactory.get();
        gallery.configureForView(entity.entityType(), entity.entityId());
        return gallery;
    }

    @Override
    public FormHandle buildGalleryForCreate(EntityType entityType, String tempSessionId) {
        AttachmentGallery gallery = galleryFactory.get();
        gallery.configureForCreate(entityType, tempSessionId);
        return new Handle(gallery);
    }

    @Override
    public FormHandle buildGalleryForEdit(EntityRef entity) {
        AttachmentGallery gallery = galleryFactory.get();
        gallery.configureForEdit(entity.entityType(), entity.entityId());
        return new Handle(gallery);
    }

    @Override
    public void openMediaLightbox(EntityRef entity) {
        List<Attachment> attachments =
                attachmentService.getByEntityId(entity.entityType(), entity.entityId());
        if (!attachments.isEmpty()) {
            lightboxFactory.get().open(attachments, 0);
        }
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
