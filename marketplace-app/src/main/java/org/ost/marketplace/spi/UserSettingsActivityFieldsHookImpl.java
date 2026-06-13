package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.common.I18nKey;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.user.services.UserService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserSettingsActivityFieldsHookImpl implements AuditActivityFieldsHook {

    private final UserService userService;
    private final I18nService i18n;

    @Override
    public EntityType entityType() {
        return EntityType.USER_SETTINGS;
    }

    @Override
    public List<ChangeEntry> expandFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item) {
        return userService.expandActivityFields(item);
    }

    @Override
    public String labelFor(@NonNull String rawFieldKey) {
        return switch (rawFieldKey) {
            case SettingsSnapshotDto.Fields.adsPageSize   -> i18n.get(I18nKey.CHANGES_SETTING_ADS_PAGE_SIZE);
            case SettingsSnapshotDto.Fields.usersPageSize -> i18n.get(I18nKey.CHANGES_SETTING_USERS_PAGE_SIZE);
            default                                       -> rawFieldKey;
        };
    }
}
