package org.ost.sqlengine.write;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

@FunctionalInterface
public interface SqlWriteCommand {

    String sql();

    static SqlWriteCommand of(String sql) {
        return () -> sql;
    }

    default void execute(JdbcClient jdbcClient, MapSqlParameterSource params) {
        jdbcClient.sql(sql()).paramSource(params).update();
    }

    // SQL must contain RETURNING id — JdbcClient.update(keyHolder) has no column array overload
    default Long executeAndReturnKey(JdbcClient jdbcClient, MapSqlParameterSource params) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql(sql()).paramSource(params).update(keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }
}
