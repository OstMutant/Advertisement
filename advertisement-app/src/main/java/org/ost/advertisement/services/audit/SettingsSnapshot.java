package org.ost.advertisement.services.audit;

import org.ost.advertisement.audit.AuditableSnapshot;
import org.ost.advertisement.audit.AuditedField;
import org.ost.advertisement.dto.UserSettings;

public record SettingsSnapshot(
        @AuditedField int adsPageSize,
        @AuditedField int usersPageSize
) implements AuditableSnapshot {
    public static SettingsSnapshot from(UserSettings settings) {
        return new SettingsSnapshot(settings.getAdsPageSize(), settings.getUsersPageSize());
    }

    @Override
    public String entityType() { return "USER_SETTINGS"; }
}
