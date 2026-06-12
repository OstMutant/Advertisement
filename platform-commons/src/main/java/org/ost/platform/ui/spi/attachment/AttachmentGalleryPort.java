package org.ost.platform.ui.spi.attachment;

import com.vaadin.flow.component.Component;
import lombok.NonNull;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

/**
 * Port: marketplace → attachment-starter.
 * Renders attachment galleries and lightboxes for arbitrary entity types.
 */
public interface AttachmentGalleryPort {

    Component buildGalleryForView(@NonNull EntityRef entity);

    FormHandle buildGalleryForCreate(@NonNull EntityType entityType, @NonNull String tempSessionId);

    FormHandle buildGalleryForEdit(@NonNull EntityRef entity);

    void openMediaLightbox(@NonNull EntityRef entity);

    interface FormHandle {
        Component getComponent();
        void commit(@NonNull EntityRef entity);
        void discard();
        void setOnChangedListener(@NonNull Runnable onChanged);
        void loadFromSnapshot(int version);
    }
}
