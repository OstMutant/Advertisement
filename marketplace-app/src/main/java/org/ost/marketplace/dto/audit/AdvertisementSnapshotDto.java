package org.ost.marketplace.dto.audit;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.api.AuditedField;
import org.ost.marketplace.entities.Advertisement;
import org.ost.platform.core.model.EntityType;

@JsonTypeName("advertisement")
public record AdvertisementSnapshotDto(
        @AuditedField String title,
        @AuditedField String description
) implements AuditableSnapshot {
    public static AdvertisementSnapshotDto from(Advertisement ad) {
        return new AdvertisementSnapshotDto(ad.getTitle(), ad.getDescription());
    }

    @Override
    public EntityType entityType() { return EntityType.ADVERTISEMENT; }
}
