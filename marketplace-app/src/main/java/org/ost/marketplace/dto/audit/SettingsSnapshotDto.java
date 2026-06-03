package org.ost.marketplace.dto.audit;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.marketplace.entities.UserSettings;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.ArrayList;
import java.util.List;

import static org.ost.platform.audit.api.AuditableSnapshot.field;
import static org.ost.platform.core.model.ChangeEntry.FieldChange;

@JsonTypeName("user_settings")
@FieldNameConstants
public record SettingsSnapshotDto(
        int adsPageSize,
        int usersPageSize
) implements AuditableSnapshot {

    public static SettingsSnapshotDto from(UserSettings settings) {
        return new SettingsSnapshotDto(settings.getAdsPageSize(), settings.getUsersPageSize());
    }

    @Override
    public EntityType entityType() { return EntityType.USER_SETTINGS; }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        SettingsSnapshotDto prev = previous instanceof SettingsSnapshotDto p ? p : null;
        Integer prevAds   = field(prev, SettingsSnapshotDto::adsPageSize);
        Integer prevUsers = field(prev, SettingsSnapshotDto::usersPageSize);
        List<ChangeEntry> changes = new ArrayList<>();
        if (prev == null || prev.adsPageSize() != adsPageSize())
            changes.add(new FieldChange(Fields.adsPageSize,   prevAds   == null ? null : String.valueOf(prevAds),   String.valueOf(adsPageSize())));
        if (prev == null || prev.usersPageSize() != usersPageSize())
            changes.add(new FieldChange(Fields.usersPageSize, prevUsers == null ? null : String.valueOf(prevUsers), String.valueOf(usersPageSize())));
        return changes;
    }

    @Override
    public List<ChangeEntry> allFields() {
        return List.of(
                new FieldChange(Fields.adsPageSize,   null, String.valueOf(adsPageSize())),
                new FieldChange(Fields.usersPageSize, null, String.valueOf(usersPageSize())));
    }
}
