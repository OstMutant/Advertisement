package org.ost.advertisement.services.audit;

import org.ost.advertisement.entities.Advertisement;

public record AdvertisementSnapshot(
        @AuditedField String title,
        @AuditedField String description
) {
    public static AdvertisementSnapshot from(Advertisement ad) {
        return new AdvertisementSnapshot(ad.getTitle(), ad.getDescription());
    }
}
