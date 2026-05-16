package org.ost.advertisement.audit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.advertisement.audit.dto.ActivityItemDto;
import org.ost.advertisement.core.spi.EntityDisplayNameResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

public class ActivityRepository {

    private final JdbcClient        jdbcClient;
    private final ActivityProjection query;

    public ActivityRepository(JdbcClient jdbcClient,
                              @Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper,
                              List<EntityDisplayNameResolver> resolvers) {
        this.jdbcClient = jdbcClient;
        this.query      = new ActivityProjection(objectMapper, resolvers);
    }

    public List<ActivityItemDto> findByUserId(Long userId) {
        return query.queryAll(jdbcClient, new MapSqlParameterSource("userId", userId));
    }
}
