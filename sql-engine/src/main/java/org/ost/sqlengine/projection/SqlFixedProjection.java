package org.ost.sqlengine.projection;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/**
 * A projection that owns its complete SQL query.
 * Use for fixed or structurally complex queries (CTEs, UNION ALL, self-joins)
 * that cannot be assembled dynamically by RepositoryCustom.
 *
 * Inherits SqlFieldDefinition mechanics and RowMapper from SqlProjection.
 * Subclasses define field constants, implement mapRow(), and provide querySql().
 */
public abstract class SqlFixedProjection<T> extends SqlProjection<T> {

    protected SqlFixedProjection(List<SqlFieldDefinition<?>> items, String sqlSource) {
        super(items, sqlSource);
    }

    public abstract String querySql();

    public List<T> queryAll(JdbcClient jdbcClient, MapSqlParameterSource params) {
        return jdbcClient.sql(querySql()).paramSource(params).query(this).list();
    }

    public Optional<T> queryOne(JdbcClient jdbcClient, MapSqlParameterSource params) {
        return jdbcClient.sql(querySql()).paramSource(params).query(this).optional();
    }
}
