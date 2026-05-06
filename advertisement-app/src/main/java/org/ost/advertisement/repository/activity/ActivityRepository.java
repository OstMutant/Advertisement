package org.ost.advertisement.repository.activity;

import org.ost.advertisement.events.dto.ActivityItemDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

@Repository
public class ActivityRepository {

    private final JdbcClient   jdbcClient;
    private final ActivityQuery query;

    public ActivityRepository(JdbcClient jdbcClient,
                               @Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.query      = new ActivityQuery(objectMapper);
    }

    public List<ActivityItemDto> findByUserId(Long userId) {
        return query.queryAll(jdbcClient, new MapSqlParameterSource("userId", userId));
    }
}
