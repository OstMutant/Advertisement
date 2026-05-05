package org.ost.advertisement.repository.activity;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ActivityRepository {

    private final JdbcClient       jdbcClient;
    private final ActivityProjection projection;

    public List<ActivityItemDto> findByUserId(Long userId) {
        return projection.queryAll(jdbcClient, new MapSqlParameterSource("userId", userId));
    }
}
