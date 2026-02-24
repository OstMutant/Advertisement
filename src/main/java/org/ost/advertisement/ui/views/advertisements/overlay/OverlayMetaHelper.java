package org.ost.advertisement.ui.views.advertisements.overlay;

import com.vaadin.flow.component.html.Div;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.ui.views.advertisements.overlay.fields.OverlayAdvertisementMetaPanel;

public class OverlayMetaHelper {

    private OverlayMetaHelper() {
    }

    public static void rebuild(Div metaContainer, OverlayAdvertisementMetaPanel.Builder builder, AdvertisementInfoDto ad) {
        metaContainer.removeAll();
        metaContainer.add(builder.build(
                OverlayAdvertisementMetaPanel.Parameters.builder()
                        .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                        .createdAt(ad.getCreatedAt())
                        .updatedAt(ad.getUpdatedAt())
                        .build()
        ));
    }
}