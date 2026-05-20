package org.ost.sqlengine.exec;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/**
 * Thin, stateless wrapper around {@link JdbcClient} that executes typed {@link SqlCommand}s.
 * All repositories delegate actual JDBC calls here.
 */
public record SqlQueryExecutor(JdbcClient jdbcClient) {

    // ── READ ──────────────────────────────────────────────────────────────────

    public <R> Optional<R> findOne(SqlCommand command, MapSqlParameterSource params, RowMapper<R> mapper) {
        return jdbcClient.sql(command.sql()).paramSource(params).query(mapper).optional();
    }

    public <R> List<R> findAll(SqlCommand command, MapSqlParameterSource params, RowMapper<R> mapper) {
        return jdbcClient.sql(command.sql()).paramSource(params).query(mapper).list();
    }

    public <R> Optional<R> findOne(SqlCommand command, MapSqlParameterSource params, Class<R> type) {
        return jdbcClient.sql(command.sql()).paramSource(params).query(type).optional();
    }

    public Long count(SqlCommand command, MapSqlParameterSource params) {
        return jdbcClient.sql(command.sql()).paramSource(params).query(Long.class).single();
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    public int executeUpdate(SqlCommand command, MapSqlParameterSource params) {
        return jdbcClient.sql(command.sql()).paramSource(params).update();
    }
}
