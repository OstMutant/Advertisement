package org.ost.sqlengine.exec;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.util.List;
import java.util.Optional;

public record RepositoryExecutor<T>(JdbcClient jdbcClient) {

    // ── READ ──────────────────────────────────────────────────────────────────

    public List<T> findAll(String sql, MapSqlParameterSource params, RowMapper<T> rowMapper) {
        return jdbcClient.sql(sql).paramSource(params).query(rowMapper).list();
    }

    public Optional<T> findOne(String sql, MapSqlParameterSource params, RowMapper<T> rowMapper) {
        return jdbcClient.sql(sql).paramSource(params).query(rowMapper).optional();
    }

    public Long count(String sql, MapSqlParameterSource params) {
        return jdbcClient.sql(sql).paramSource(params).query(Long.class).single();
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    public void execute(String sql, MapSqlParameterSource params) {
        jdbcClient.sql(sql).paramSource(params).update();
    }

    // SQL must contain RETURNING id — JdbcClient.update(keyHolder) has no column array overload
    public Long executeAndReturnKey(String sql, MapSqlParameterSource params) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql(sql).paramSource(params).update(keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }
}
