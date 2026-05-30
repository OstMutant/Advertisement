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
import static org.ost.platform.core.model.ChangeEntry.SettingChange;

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
        List<ChangeEntry> changes = new ArrayList<>();
        Integer prevAds   = field(prev, SettingsSnapshotDto::adsPageSize);
        Integer prevUsers = field(prev, SettingsSnapshotDto::usersPageSize);
        if (prev == null || prev.adsPageSize() != adsPageSize())
            changes.add(new SettingChange(Fields.adsPageSize,   prevAds,   adsPageSize()));
        if (prev == null || prev.usersPageSize() != usersPageSize())
            changes.add(new SettingChange(Fields.usersPageSize, prevUsers, usersPageSize()));
        return changes;
    }
}
