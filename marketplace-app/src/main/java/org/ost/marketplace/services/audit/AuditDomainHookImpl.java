package org.ost.marketplace.services.audit;

import org.ost.marketplace.repository.advertisement.AdvertisementRepository;
import org.ost.marketplace.repository.user.UserRepository;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AuditDomainHookImpl implements AuditDomainHook {

    private final AdvertisementRepository advertisementRepository;
    private final UserRepository          userRepository;

    public AuditDomainHookImpl(AdvertisementRepository advertisementRepository,
                                UserRepository userRepository) {
        this.advertisementRepository = advertisementRepository;
        this.userRepository          = userRepository;
    }

    @Override
    public Map<Long, String> resolveNames(Set<Long> actorIds) {
        if (actorIds.isEmpty()) return Map.of();
        return userRepository.findActorNames(actorIds.toArray(new Long[0]));
    }

    @Override
    public Set<Long> findExisting(EntityType entityType, Set<Long> entityIds) {
        if (entityIds.isEmpty()) return Set.of();
        Long[] ids = entityIds.toArray(new Long[0]);
        List<Long> found = switch (entityType) {
            case ADVERTISEMENT            -> advertisementRepository.findExistingIds(ids);
            case USER, USER_SETTINGS      -> userRepository.findExistingIds(ids);
        };
        return Set.copyOf(found);
    }
}
