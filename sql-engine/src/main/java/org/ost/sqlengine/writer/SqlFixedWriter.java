package org.ost.sqlengine.writer;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

@FunctionalInterface
public interface SqlFixedWriter {

    String sql();

    static SqlFixedWriter of(String sql) {
        return () -> sql;
    }

    default void execute(JdbcClient jdbcClient, MapSqlParameterSource params) {
        jdbcClient.sql(sql()).paramSource(params).update();
    }

    // SQL must contain RETURNING id — JdbcClient.update(keyHolder) has no column array overload
    default Long executeAndReturnKey(JdbcClient jdbcClient, MapSqlParameterSource params) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql(sql()).paramSource(params).update(keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }
}
