package org.ost.marketplace.ui.views.components.attachment;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

import java.util.List;

@SpringComponent
@RequiredArgsConstructor
public class AttachmentGalleryService {

    private final UiComponentFactory<AttachmentGallery> galleryFactory;
    private final UiComponentFactory<CardMediaLightbox> lightboxFactory;
    private final ComponentFactory<AttachmentPort>     attachmentPortFactory;

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
        List<AttachmentItemDto> attachments =
                attachmentPortFactory.get().getByEntityId(entity.entityType(), entity.entityId());
        if (!attachments.isEmpty()) {
            lightboxFactory.get().open(attachments, 0);
        }
    }

    public interface FormHandle {
        Component getComponent();
        Long commit(@NonNull EntityRef entity);
        void discard();
        void setOnChangedListener(@NonNull Runnable onChanged);
        void loadFromSnapshotId(Long snapshotId);
    }

    private static final class Handle implements FormHandle {
        private final AttachmentGallery gallery;

        Handle(AttachmentGallery gallery) { this.gallery = gallery; }

        @Override public Component getComponent() { return gallery; }
        @Override public Long commit(@NonNull EntityRef entity) {
            return gallery.commitTempUploads(entity.entityType(), entity.entityId());
        }
        @Override public void discard() { gallery.discardTempUploads(); }
        @Override public void setOnChangedListener(@NonNull Runnable onChanged) {
            gallery.setOnChangedListener(onChanged);
        }
        @Override public void loadFromSnapshotId(Long snapshotId) { gallery.loadFromSnapshotId(snapshotId); }
    }
}
