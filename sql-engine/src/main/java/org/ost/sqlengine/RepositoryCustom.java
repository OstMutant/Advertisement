package org.ost.sqlengine;

import org.ost.sqlengine.exec.SqlQueryExecutor;
import org.ost.sqlengine.exec.SqlQueryBuilder;
import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.projection.SqlEntityProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Optional;


public class RepositoryCustom<T, F> {

    private final SqlEntityProjection<T> sqlProjection;
    private final SqlFilterBuilder<F> filterBuilder;
    private final SqlQueryBuilder sqlQueryBuilder;
    private final SqlQueryExecutor<T> executor;

    protected RepositoryCustom(JdbcClient jdbcClient, SqlEntityProjection<T> sqlProjection,
                               SqlFilterBuilder<F> filterBuilder) {
        this.sqlProjection = sqlProjection;
        this.filterBuilder = filterBuilder;
        this.sqlQueryBuilder = new SqlQueryBuilder();
        this.executor = new SqlQueryExecutor<>(jdbcClient);
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

    public <C> Optional<T> find(SqlFilterBuilder<C> customApplier, C filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                customApplier.build(params, filter)
        );
        return executor.findOne(sql, params, sqlProjection);
    }

    protected Optional<T> findOne(String where, MapSqlParameterSource params) {
        String sql = sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                where
        );
        return executor.findOne(sql, params, sqlProjection);
    }

    protected void execute(String sql, MapSqlParameterSource params) {
        executor.execute(sql, params);
    }

    private String pageableToSql(MapSqlParameterSource params, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) return "";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        return "LIMIT :limit OFFSET :offset";
    }
}
