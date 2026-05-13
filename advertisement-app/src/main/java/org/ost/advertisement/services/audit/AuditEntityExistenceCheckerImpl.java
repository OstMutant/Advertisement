package org.ost.advertisement.services.audit;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.model.EntityType;
import org.ost.advertisement.events.spi.AuditEntityExistenceChecker;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuditEntityExistenceCheckerImpl implements AuditEntityExistenceChecker {

    private final JdbcClient jdbcClient;

    @Override
    public Set<Long> findExisting(EntityType entityType, Set<Long> entityIds) {
        if (entityIds.isEmpty()) return Set.of();
        String sql = switch (entityType) {
            case ADVERTISEMENT ->
                "SELECT id FROM advertisement WHERE id = ANY(:ids) AND deleted_at IS NULL";
            case USER, USER_SETTINGS ->
                "SELECT id FROM user_information WHERE id = ANY(:ids)";
        };
        return jdbcClient.sql(sql)
                .param("ids", entityIds.toArray(new Long[0]))
                .query((rs, _) -> rs.getLong("id"))
                .list()
                .stream()
                .collect(Collectors.toSet());
    }
}
