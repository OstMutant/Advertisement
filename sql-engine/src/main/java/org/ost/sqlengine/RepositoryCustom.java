package org.ost.sqlengine;

import org.ost.sqlengine.common.SqlCommand;
import org.ost.sqlengine.read.SqlFixedQuery;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/**
 * Base repository for hand-written SQL queries. Composes {@link SqlQueryExecutor} and exposes
 * typed read/write methods. Use directly for simple bespoke queries; extend via
 * {@link FilterableRepository} when dynamic filtering and pagination are needed.
 */
public class RepositoryCustom {

    protected final SqlQueryExecutor executor;

    public RepositoryCustom(JdbcClient jdbcClient) {
        this.executor = new SqlQueryExecutor(jdbcClient);
    }

    public int executeUpdate(SqlCommand command, MapSqlParameterSource params) {
        return executor.executeUpdate(command, params);
    }

    public <R> Optional<R> findOne(SqlCommand command, MapSqlParameterSource params, RowMapper<R> mapper) {
        return executor.findOne(command, params, mapper);
    }

    public <R> List<R> findAll(SqlCommand command, MapSqlParameterSource params, RowMapper<R> mapper) {
        return executor.findAll(command, params, mapper);
    }

    public <R> Optional<R> findOne(SqlCommand command, MapSqlParameterSource params, Class<R> type) {
        return executor.findOne(command, params, type);
    }

    public <R> List<R> queryAll(SqlFixedQuery<R> query, MapSqlParameterSource params) {
        return executor.queryAll(query, params);
    }
}
