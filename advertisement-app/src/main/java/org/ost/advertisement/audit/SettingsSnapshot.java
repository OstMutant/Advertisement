package org.ost.advertisement.audit;

import org.ost.advertisement.dto.UserSettings;

public record SettingsSnapshot(
        @AuditedField int adsPageSize,
        @AuditedField int usersPageSize
) {
    public static SettingsSnapshot from(UserSettings settings) {
        return new SettingsSnapshot(settings.getAdsPageSize(), settings.getUsersPageSize());
    }
}
