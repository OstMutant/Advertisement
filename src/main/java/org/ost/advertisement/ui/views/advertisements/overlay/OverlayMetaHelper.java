package org.ost.advertisement.ui.views.advertisements.overlay;

import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementMetaPanel;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;

public class OverlayMetaHelper {

    private OverlayMetaHelper() {}

    public static void rebuild(OverlayLayout layout, OverlayAdvertisementMetaPanel.Builder builder, AdvertisementInfoDto ad) {
        layout.getMetaContainer().removeAll();
        layout.getMetaContainer().add(builder.build(
                OverlayAdvertisementMetaPanel.Parameters.builder()
                        .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                        .createdAt(ad.getCreatedAt())
                        .updatedAt(ad.getUpdatedAt())
                        .build()
        ));
    }
}