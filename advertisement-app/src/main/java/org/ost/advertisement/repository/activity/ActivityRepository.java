package org.ost.advertisement.repository.activity;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ActivityRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ActivityProjection         projection;

    public List<ActivityItemDto> findByUserId(Long userId) {
        return projection.queryAll(jdbc, new MapSqlParameterSource("userId", userId));
    }
}
