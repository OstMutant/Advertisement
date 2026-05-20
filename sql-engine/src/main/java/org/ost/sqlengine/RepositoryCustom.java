package org.ost.sqlengine;

import org.ost.sqlengine.exec.SqlCommand;
import org.ost.sqlengine.exec.SqlQueryExecutor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

public class RepositoryCustom {

    private final SqlQueryExecutor executor;

    public RepositoryCustom(JdbcClient jdbcClient) {
        this.executor = new SqlQueryExecutor(jdbcClient);
    }

    public void execute(SqlCommand command, MapSqlParameterSource params) {
        executor.execute(command, params);
    }

    public int executeUpdate(SqlCommand command, MapSqlParameterSource params) {
        return executor.jdbcClient().sql(command.sql()).paramSource(params).update();
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

    public JdbcClient jdbcClient() {
        return executor.jdbcClient();
    }
}
