package org.ost.ui.attachment;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.services.AttachmentService;
import org.ost.ui.attachment.AttachmentGallery;
import org.ost.ui.attachment.CardMediaLightbox;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

import java.util.List;

@SpringComponent
@RequiredArgsConstructor
public class AttachmentGalleryService {

    private final ComponentFactory<AttachmentGallery>  galleryFactory;
    private final ComponentFactory<CardMediaLightbox>  lightboxFactory;
    private final AttachmentService                    attachmentService;

    public Component buildGalleryForView(@NonNull EntityRef entity) {
        AttachmentGallery gallery = galleryFactory.get();
        gallery.configureForView(entity.entityType(), entity.entityId());
        return gallery;
    }

    public FormHandle buildGalleryForCreate(@NonNull EntityType entityType, @NonNull String tempSessionId) {
        AttachmentGallery gallery = galleryFactory.get();
        gallery.configureForCreate(entityType, tempSessionId);
        return new Handle(gallery);
    }

    public FormHandle buildGalleryForEdit(@NonNull EntityRef entity) {
        AttachmentGallery gallery = galleryFactory.get();
        gallery.configureForEdit(entity.entityType(), entity.entityId());
        return new Handle(gallery);
    }

    public void openMediaLightbox(@NonNull EntityRef entity) {
        List<Attachment> attachments =
                attachmentService.getByEntityId(entity.entityType(), entity.entityId());
        if (!attachments.isEmpty()) {
            lightboxFactory.get().open(attachments, 0);
        }
    }

    public interface FormHandle {
        Component getComponent();
        void commit(@NonNull EntityRef entity);
        void discard();
        void setOnChangedListener(@NonNull Runnable onChanged);
        void loadFromSnapshot(int version);
    }

    private static final class Handle implements FormHandle {
        private final AttachmentGallery gallery;

        Handle(AttachmentGallery gallery) { this.gallery = gallery; }

        @Override public Component getComponent() { return gallery; }
        @Override public void commit(@NonNull EntityRef entity) {
            gallery.commitTempUploads(entity.entityType(), entity.entityId());
        }
        @Override public void discard() { gallery.discardTempUploads(); }
        @Override public void setOnChangedListener(@NonNull Runnable onChanged) {
            gallery.setOnChangedListener(onChanged);
        }
        @Override public void loadFromSnapshot(int version) { gallery.loadFromSnapshot(version); }
    }
}
