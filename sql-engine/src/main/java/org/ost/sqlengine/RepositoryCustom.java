package org.ost.sqlengine;

import org.ost.sqlengine.exec.RepositoryExecutor;
import org.ost.sqlengine.exec.SqlQueryBuilder;
import org.ost.sqlengine.filter.FilterBuilder;
import org.ost.sqlengine.projection.SqlProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;


public class RepositoryCustom<T, F> {

    protected final SqlProjection<T> sqlProjection;
    protected final FilterBuilder<F> filterBuilder;
    protected final SqlQueryBuilder sqlQueryBuilder;
    protected final RepositoryExecutor<T> executor;

    protected RepositoryCustom(NamedParameterJdbcTemplate jdbc, SqlProjection<T> sqlProjection,
                               FilterBuilder<F> filterBuilder) {
        this.sqlProjection = sqlProjection;
        this.filterBuilder = filterBuilder;
        this.sqlQueryBuilder = new SqlQueryBuilder();
        this.executor = new RepositoryExecutor<>(jdbc);
    }

    public List<T> findByFilter(F filter, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                filterBuilder.build(params, filter),
                sqlProjection.getOrderByClause(pageable.getSort()),
                pageableToSql(params, pageable)
        );
        return executor.findAll(sql, params, sqlProjection);
    }

    public Long countByFilter(F filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = sqlQueryBuilder.count(
                sqlProjection.getCountSource(),
                filterBuilder.build(params, filter)
        );
        return executor.count(sql, params);
    }

    public <C> Optional<T> find(FilterBuilder<C> customApplier, C filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                customApplier.build(params, filter)
        );
        return executor.findOne(sql, params, sqlProjection);
    }

    private String pageableToSql(MapSqlParameterSource params, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) return "";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        return "LIMIT :limit OFFSET :offset";
    }
}
