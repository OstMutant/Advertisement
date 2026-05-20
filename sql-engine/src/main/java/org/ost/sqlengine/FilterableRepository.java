package org.ost.sqlengine;

import org.ost.sqlengine.exec.SqlCommand;
import org.ost.sqlengine.exec.SqlQueryBuilder;
import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.read.SqlEntityProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/**
 * Extends {@link RepositoryCustom} with dynamic filter + pagination support.
 * Combines an {@link SqlEntityProjection} (SELECT clause + FROM source) with a
 * {@link SqlFilterBuilder} (WHERE clause) to produce pageable queries at runtime.
 *
 * @param <T> the result DTO type
 * @param <F> the filter DTO type
 */
public class FilterableRepository<T, F> extends RepositoryCustom {

    private final SqlEntityProjection<T> sqlProjection;
    private final SqlFilterBuilder<F>    filterBuilder;
    private final SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder();
    public FilterableRepository(JdbcClient jdbcClient,
                                SqlEntityProjection<T> projection,
                                SqlFilterBuilder<F> filterBuilder) {
        super(jdbcClient);
        this.sqlProjection   = projection;
        this.filterBuilder   = filterBuilder;
    }

    public List<T> findByFilter(F filter, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        SqlCommand sql = SqlCommand.of(sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                filterBuilder.build(params, filter),
                sqlProjection.getOrderByClause(pageable.getSort()),
                pageableToSql(params, pageable)
        ));
        return executor.findAll(sql, params, sqlProjection);
    }

    public Long countByFilter(F filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        SqlCommand sql = SqlCommand.of(sqlQueryBuilder.count(
                sqlProjection.getCountSource(),
                filterBuilder.build(params, filter)
        ));
        return executor.count(sql, params);
    }

    public <C> Optional<T> find(SqlFilterBuilder<C> customApplier, C filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        SqlCommand sql = SqlCommand.of(sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                customApplier.build(params, filter)
        ));
        return executor.findOne(sql, params, sqlProjection);
    }

    public Optional<T> findOne(String where, MapSqlParameterSource params) {
        SqlCommand sql = SqlCommand.of(sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                where
        ));
        return executor.findOne(sql, params, sqlProjection);
    }

    private String pageableToSql(MapSqlParameterSource params, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) return "";
        params.addValue("limit",  pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        return "LIMIT :limit OFFSET :offset";
    }
}
