package org.ost.marketplace.services.audit;

import org.ost.marketplace.repository.user.UserRepository;
import org.ost.platform.audit.spi.AuditActorNameResolver;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class AuditActorNameResolverImpl implements AuditActorNameResolver {

    private final UserRepository userRepository;

    public AuditActorNameResolverImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Map<Long, String> resolveNames(Set<Long> actorIds) {
        if (actorIds.isEmpty()) return Map.of();
        return userRepository.findActorNames(actorIds.toArray(new Long[0]));
    }
}
