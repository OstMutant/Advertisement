package org.ost.sqlengine.read;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

/**
 * Projection that owns its complete, hand-written SQL query.
 * Use for structurally complex queries (CTEs, UNION ALL, self-joins) that cannot be
 * assembled dynamically by {@link org.ost.sqlengine.FilterableRepository}.
 *
 * <p>Subclasses declare {@link SqlSelectField} constants, implement {@code mapRow()},
 * and provide the full SQL via {@code querySql()}.</p>
 */
public abstract class SqlFixedQuery<T> extends SqlBaseProjection<T> {

    protected SqlFixedQuery(List<SqlSelectField<?>> items) {
        super(items);
    }

    public abstract String querySql();

    public List<T> queryAll(JdbcClient jdbcClient, MapSqlParameterSource params) {
        return jdbcClient.sql(querySql()).paramSource(params).query(this).list();
    }
}
