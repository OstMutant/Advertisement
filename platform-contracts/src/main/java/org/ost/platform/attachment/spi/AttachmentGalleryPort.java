package org.ost.platform.attachment.spi;

import com.vaadin.flow.component.Component;
import org.ost.platform.core.model.EntityType;

/**
 * Port: marketplace → attachment-starter.
 * Renders attachment galleries and lightboxes for arbitrary entity types.
 */
public interface AttachmentGalleryPort {

    Component buildGalleryForView(EntityType entityType, Long entityId);

    FormHandle buildGalleryForCreate(EntityType entityType, String tempSessionId);

    FormHandle buildGalleryForEdit(EntityType entityType, Long entityId);

    void openMediaLightbox(EntityType entityType, Long entityId);

    interface FormHandle {
        Component getComponent();
        void commit(EntityType entityType, Long entityId);
        void discard();
    }
}
