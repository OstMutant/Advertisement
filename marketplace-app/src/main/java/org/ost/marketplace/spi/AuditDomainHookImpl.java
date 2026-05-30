package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.AdvertisementService;
import org.ost.marketplace.services.user.UserService;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuditDomainHookImpl implements AuditDomainHook {

    private final AdvertisementService advertisementService;
    private final UserService          userService;

    @Override
    public Map<Long, String> resolveNames(@NonNull Set<Long> actorIds) {
        return userService.findActorNames(actorIds);
    }

    @Override
    public Set<Long> findExisting(@NonNull EntityType entityType, @NonNull Set<Long> entityIds) {
        Long[] ids = entityIds.toArray(new Long[0]);
        return Set.copyOf(switch (entityType) {
            case ADVERTISEMENT       -> advertisementService.findExistingIds(ids);
            case USER, USER_SETTINGS -> userService.findExistingIds(ids);
        });
    }
}
