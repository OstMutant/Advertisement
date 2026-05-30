package org.ost.marketplace.dto.audit;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.marketplace.entities.Advertisement;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.ost.platform.audit.api.AuditableSnapshot.field;
import static org.ost.platform.audit.api.AuditableSnapshot.trunc;
import static org.ost.platform.core.model.ChangeEntry.FieldChange;

@JsonTypeName("advertisement")
@FieldNameConstants
public record AdvertisementSnapshotDto(
        String title,
        String description
) implements AuditableSnapshot {

    public static AdvertisementSnapshotDto from(Advertisement ad) {
        return new AdvertisementSnapshotDto(ad.getTitle(), ad.getDescription());
    }

    @Override
    public EntityType entityType() { return EntityType.ADVERTISEMENT; }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        AdvertisementSnapshotDto prev = previous instanceof AdvertisementSnapshotDto p ? p : null;
        List<ChangeEntry> changes = new ArrayList<>();
        String prevTitle = field(prev, AdvertisementSnapshotDto::title);
        String prevDesc  = field(prev, AdvertisementSnapshotDto::description);
        if (!Objects.equals(prevTitle, title()))
            changes.add(new FieldChange(Fields.title, trunc(prevTitle), trunc(title())));
        if (!Objects.equals(prevDesc, description()))
            changes.add(new FieldChange(Fields.description, trunc(prevDesc), trunc(description())));
        return changes;
    }
}
