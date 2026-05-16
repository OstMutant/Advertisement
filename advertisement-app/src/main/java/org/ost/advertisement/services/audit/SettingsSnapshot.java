package org.ost.advertisement.services.audit;

import org.ost.advertisement.audit.api.AuditableSnapshot;
import org.ost.advertisement.audit.api.AuditedField;
import org.ost.advertisement.core.config.UserSettings;
import org.ost.advertisement.core.model.EntityType;

public record SettingsSnapshot(
        @AuditedField int adsPageSize,
        @AuditedField int usersPageSize
) implements AuditableSnapshot {
    public static SettingsSnapshot from(UserSettings settings) {
        return new SettingsSnapshot(settings.getAdsPageSize(), settings.getUsersPageSize());
    }

    @Override
    public EntityType entityType() { return EntityType.USER_SETTINGS; }
}
