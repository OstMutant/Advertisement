package org.ost.advertisement.repository;

import org.ost.advertisement.repository.query.exec.RepositoryExecutor;
import org.ost.advertisement.repository.query.exec.SqlQueryBuilder;
import org.ost.advertisement.repository.query.filter.FilterBuilder;
import org.ost.advertisement.repository.query.projection.SqlProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

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
                sqlProjection.getSqlSource(),
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
        return ofNullable(pageable)
                .filter(p -> !p.isUnpaged())
                .map(p -> {
                    params.addValue("limit", p.getPageSize());
                    params.addValue("offset", p.getOffset());
                    return "LIMIT :limit OFFSET :offset";
                })
                .orElse("");
    }
}
