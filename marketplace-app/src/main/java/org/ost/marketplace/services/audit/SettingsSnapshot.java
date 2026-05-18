package org.ost.marketplace.services.audit;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.api.AuditedField;
import org.ost.platform.core.config.UserSettings;
import org.ost.platform.core.model.EntityType;

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
