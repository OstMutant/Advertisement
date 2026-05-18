package org.ost.marketplace.services.audit;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.api.AuditedField;
import org.ost.marketplace.entities.Advertisement;
import org.ost.platform.core.model.EntityType;

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
