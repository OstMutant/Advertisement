package org.ost.advertisement.repository.query.exec;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;

public record RepositoryExecutor<T>(NamedParameterJdbcTemplate jdbc) {

    public List<T> findAll(String sql, MapSqlParameterSource params, RowMapper<T> rowMapper) {
        return jdbc.query(sql, params, rowMapper);
    }

    public Optional<T> findOne(String sql, MapSqlParameterSource params, RowMapper<T> rowMapper) {
        return jdbc.query(sql, params, rowMapper).stream().findFirst();
    }

    public Long count(String sql, MapSqlParameterSource params) {
        return jdbc.queryForObject(sql, params, Long.class);
    }
}
