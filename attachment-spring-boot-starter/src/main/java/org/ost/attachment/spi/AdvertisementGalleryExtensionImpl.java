package org.ost.attachment.spi;

import com.vaadin.flow.component.Component;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.spi.AdvertisementGalleryExtension;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.ui.AttachmentGallery;
import org.ost.attachment.ui.CardPhotoLightbox;
import org.ost.storage.api.ConditionalOnStorageEnabled;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class AdvertisementGalleryExtensionImpl implements AdvertisementGalleryExtension {

    private final ObjectProvider<AttachmentGallery> galleryProvider;
    private final AttachmentService                  attachmentService;

    @Override
    public Component buildGalleryForView(Long adId) {
        AttachmentGallery gallery = galleryProvider.getObject();
        gallery.configureForView(adId);
        return gallery;
    }

    @Override
    public FormHandle buildGalleryForCreate(String tempSessionId) {
        AttachmentGallery gallery = galleryProvider.getObject();
        gallery.configureForCreate(tempSessionId);
        return new Handle(gallery);
    }

    @Override
    public FormHandle buildGalleryForEdit(Long adId) {
        AttachmentGallery gallery = galleryProvider.getObject();
        gallery.configureForEdit(adId);
        return new Handle(gallery);
    }

    @Override
    public void openPhotoLightbox(Long adId) {
        CardPhotoLightbox.open(attachmentService.getByEntityId(adId), 0);
    }

    private record Handle(AttachmentGallery gallery) implements FormHandle {
        @Override public Component getComponent()        { return gallery; }
        @Override public void commit(Long entityId)      { gallery.commitTempUploads(entityId); }
        @Override public void discard()                  { gallery.discardTempUploads(); }
    }
}
