package org.ost.advertisement.events.spi;

import com.vaadin.flow.component.Component;

public interface AdvertisementGalleryExtension {

    Component buildGalleryForView(Long adId);

    FormHandle buildGalleryForCreate(String tempSessionId);

    FormHandle buildGalleryForEdit(Long adId);

    void openPhotoLightbox(Long adId);

    interface FormHandle {
        Component getComponent();
        void commit(Long entityId);
        void discard();
    }
}
