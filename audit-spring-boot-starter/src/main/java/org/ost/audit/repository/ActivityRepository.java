package org.ost.audit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.spi.EntityDisplayNameResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

public class ActivityRepository {

    private final JdbcClient        jdbcClient;
    private final ActivityProjection query;

    public ActivityRepository(JdbcClient jdbcClient,
                              @Qualifier("auditObjectMapper") ObjectMapper objectMapper,
                              List<EntityDisplayNameResolver> resolvers) {
        this.jdbcClient = jdbcClient;
        this.query      = new ActivityProjection(objectMapper, resolvers);
    }

    public List<ActivityItemDto> findByActorId(Long actorId) {
        return query.queryAll(jdbcClient, new MapSqlParameterSource("actorId", actorId));
    }
}
