package org.ost.marketplace.services.audit;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.user.UserService;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.spi.ActivityFieldsHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityFieldsHookImpl implements ActivityFieldsHook {

    private final UserService userService;

    @Override
    public boolean supports(EntityType entityType) {
        return entityType == EntityType.USER;
    }

    @Override
    public List<ChangeEntry> expandFields(ActivityItemDto item) {
        return userService.expandActivityFields(item);
    }
}
