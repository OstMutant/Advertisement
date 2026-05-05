package org.ost.sqlengine.projection;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

/**
 * A projection that owns its complete SQL query.
 * Use for fixed or structurally complex queries (CTEs, UNION ALL, self-joins)
 * that cannot be assembled dynamically by RepositoryCustom.
 *
 * Subclasses define field constants, implement mapRow(), and provide querySql().
 */
public abstract class SqlFixedQuery<T> extends SqlEntityProjection<T> {

    protected SqlFixedQuery(List<SqlSelectField<?>> items) {
        super(items, "");
    }

    protected SqlFixedQuery(List<SqlSelectField<?>> items, String sqlSource) {
        super(items, sqlSource);
    }

    public abstract String querySql();

    public List<T> queryAll(JdbcClient jdbcClient, MapSqlParameterSource params) {
        return jdbcClient.sql(querySql()).paramSource(params).query(this).list();
    }
}
