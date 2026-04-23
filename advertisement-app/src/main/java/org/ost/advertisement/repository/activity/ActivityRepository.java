package org.ost.advertisement.repository.activity;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.ActivityItemDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ActivityRepository {

    private static final ActivityProjection PROJECTION = new ActivityProjection();

    private final NamedParameterJdbcTemplate jdbc;

    public List<ActivityItemDto> findByUserId(Long userId) {
        return PROJECTION.queryAll(jdbc, new MapSqlParameterSource("userId", userId));
    }
}
