package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.common.I18nKey;
import org.ost.user.dto.audit.UserSnapshotDto;
import org.ost.user.services.UserService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserActivityFieldsHookImpl implements AuditActivityFieldsHook {

    private final UserService userService;
    private final I18nService i18n;

    @Override
    public EntityType entityType() {
        return EntityType.USER;
    }

    @Override
    public List<ChangeEntry> expandFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item) {
        return userService.expandActivityFields(item);
    }

    @Override
    public String labelFor(@NonNull String rawFieldKey) {
        return switch (rawFieldKey) {
            case UserSnapshotDto.Fields.name  -> i18n.get(I18nKey.CHANGES_FIELD_NAME);
            case UserSnapshotDto.Fields.email -> i18n.get(I18nKey.CHANGES_FIELD_EMAIL);
            case UserSnapshotDto.Fields.role  -> i18n.get(I18nKey.CHANGES_FIELD_ROLE);
            default                           -> rawFieldKey;
        };
    }
}
