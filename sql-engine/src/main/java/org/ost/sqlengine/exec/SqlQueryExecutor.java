package org.ost.sqlengine.exec;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.util.List;
import java.util.Optional;

public record SqlQueryExecutor<T>(JdbcClient jdbcClient) {

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

    public int execute(String sql, MapSqlParameterSource params) {
        return jdbcClient.sql(sql).paramSource(params).update();
    }
}
