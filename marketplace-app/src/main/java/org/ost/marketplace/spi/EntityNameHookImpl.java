package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.AdvertisementService;
import org.ost.marketplace.services.user.UserService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.EntityNameHook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntityNameHookImpl implements EntityNameHook {

    private final AdvertisementService advertisementService;
    private final UserService          userService;

    @Override
    public boolean supports(EntityType entityType) {
        return entityType == EntityType.ADVERTISEMENT
                || entityType == EntityType.USER
                || entityType == EntityType.USER_SETTINGS;
    }

    @Override
    public String resolveDisplayName(EntityType entityType, AuditableSnapshot snapshot) {
        return switch (entityType) {
            case ADVERTISEMENT       -> advertisementService.resolveDisplayName(snapshot);
            case USER, USER_SETTINGS -> userService.resolveDisplayName(entityType, snapshot);
        };
    }
}
