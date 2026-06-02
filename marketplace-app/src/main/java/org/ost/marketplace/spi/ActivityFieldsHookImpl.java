package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.user.UserService;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityFieldsHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityFieldsHookImpl implements AuditActivityFieldsHook {

    private final UserService userService;

    @Override
    public boolean supports(EntityType entityType) {
        return entityType == EntityType.USER || entityType == EntityType.USER_SETTINGS;
    }

    @Override
    public List<ChangeEntry> expandFields(AuditActivityItemDto item) {
        return userService.expandActivityFields(item);
    }
}
