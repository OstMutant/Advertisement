package org.ost.marketplace.services.audit;

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
    public Map<Long, String> resolveNames(Set<Long> actorIds) {
        if (actorIds.isEmpty()) return Map.of();
        return userService.findActorNames(actorIds);
    }

    @Override
    public Set<Long> findExisting(EntityType entityType, Set<Long> entityIds) {
        if (entityIds.isEmpty()) return Set.of();
        Long[] ids = entityIds.toArray(new Long[0]);
        return Set.copyOf(switch (entityType) {
            case ADVERTISEMENT       -> advertisementService.findExistingIds(ids);
            case USER, USER_SETTINGS -> userService.findExistingIds(ids);
        });
    }
}
