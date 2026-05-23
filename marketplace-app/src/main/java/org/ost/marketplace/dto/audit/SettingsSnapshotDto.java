package org.ost.marketplace.dto.audit;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.api.AuditedField;
import org.ost.marketplace.entities.UserSettings;
import org.ost.platform.core.model.EntityType;

public record SettingsSnapshotDto(
        @AuditedField int adsPageSize,
        @AuditedField int usersPageSize
) implements AuditableSnapshot {
    public static SettingsSnapshotDto from(UserSettings settings) {
        return new SettingsSnapshotDto(settings.getAdsPageSize(), settings.getUsersPageSize());
    }

    @Override
    public EntityType entityType() { return EntityType.USER_SETTINGS; }
}
