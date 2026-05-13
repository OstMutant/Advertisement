package org.ost.advertisement.services.audit;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.spi.AuditActorNameResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuditActorNameResolverImpl implements AuditActorNameResolver {

    private final JdbcClient jdbcClient;

    @Override
    public Map<Long, String> resolveNames(Set<Long> actorIds) {
        if (actorIds.isEmpty()) return Map.of();
        return jdbcClient.sql("SELECT id, name FROM user_information WHERE id = ANY(:ids)")
                .param("ids", actorIds.toArray(new Long[0]))
                .query((rs, _) -> Map.entry(rs.getLong("id"), rs.getString("name")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
