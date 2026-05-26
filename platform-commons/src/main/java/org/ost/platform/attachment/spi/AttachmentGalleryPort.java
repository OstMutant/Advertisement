package org.ost.platform.attachment.spi;

import com.vaadin.flow.component.Component;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

/**
 * Port: marketplace → attachment-starter.
 * Renders attachment galleries and lightboxes for arbitrary entity types.
 */
public interface AttachmentGalleryPort {

    Component buildGalleryForView(EntityRef entity);

    FormHandle buildGalleryForCreate(EntityType entityType, String tempSessionId);

    FormHandle buildGalleryForEdit(EntityRef entity);

    void openMediaLightbox(EntityRef entity);

    interface FormHandle {
        Component getComponent();
        void commit(EntityRef entity);
        void discard();
    }
}
