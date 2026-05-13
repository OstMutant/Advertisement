package org.ost.advertisement.services.audit;

import org.ost.advertisement.audit.AuditableSnapshot;
import org.ost.advertisement.audit.AuditedField;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.events.model.EntityType;

public record AdvertisementSnapshot(
        @AuditedField String title,
        @AuditedField String description
) implements AuditableSnapshot {
    public static AdvertisementSnapshot from(Advertisement ad) {
        return new AdvertisementSnapshot(ad.getTitle(), ad.getDescription());
    }

    @Override
    public EntityType entityType() { return EntityType.ADVERTISEMENT; }
}
