package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.services.user.UserService;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityFieldsHookImpl implements AuditActivityFieldsHook {

    private final UserService userService;
    private final I18nService i18n;

    @Override
    public boolean supports(EntityType entityType) {
        return entityType == EntityType.USER || entityType == EntityType.USER_SETTINGS;
    }

    @Override
    public List<ChangeEntry> expandFields(AuditActivityItemDto item) {
        return userService.expandActivityFields(item);
    }

    @Override
    public String labelFor(String rawFieldKey) {
        return switch (rawFieldKey) {
            case "name"          -> i18n.get(I18nKey.CHANGES_FIELD_NAME);
            case "email"         -> i18n.get(I18nKey.CHANGES_FIELD_EMAIL);
            case "role"          -> i18n.get(I18nKey.CHANGES_FIELD_ROLE);
            case "adsPageSize"   -> i18n.get(I18nKey.CHANGES_SETTING_ADS_PAGE_SIZE);
            case "usersPageSize" -> i18n.get(I18nKey.CHANGES_SETTING_USERS_PAGE_SIZE);
            default              -> rawFieldKey;
        };
    }
}
