package org.ost.marketplace.services.audit;

import org.ost.marketplace.repository.user.UserDescriptor;
import org.ost.platform.audit.spi.AuditActorNameResolver;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AuditActorNameResolverImpl implements AuditActorNameResolver {

    private final RepositoryCustom repo;

    public AuditActorNameResolverImpl(JdbcClient jdbcClient) {
        this.repo = new RepositoryCustom(jdbcClient);
    }

    @Override
    public Map<Long, String> resolveNames(Set<Long> actorIds) {
        if (actorIds.isEmpty()) return Map.of();
        return repo.findAll(
                        UserDescriptor.Read.SELECT_ACTOR_NAMES,
                        UserDescriptor.Read.idsParams(actorIds.toArray(new Long[0])),
                        (rs, _) -> Map.entry(rs.getLong("id"), rs.getString("name")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
