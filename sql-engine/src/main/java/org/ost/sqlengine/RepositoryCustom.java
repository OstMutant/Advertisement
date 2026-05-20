package org.ost.sqlengine;

import org.ost.sqlengine.exec.SqlCommand;
import org.ost.sqlengine.exec.SqlQueryBuilder;
import org.ost.sqlengine.exec.SqlQueryExecutor;
import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.read.SqlEntityProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

public class RepositoryCustom<T, F> {

    private final SqlEntityProjection<T> sqlProjection;
    private final SqlFilterBuilder<F>    filterBuilder;
    private final SqlQueryBuilder        sqlQueryBuilder;
    private final SqlQueryExecutor<T>    executor;

    protected RepositoryCustom(JdbcClient jdbcClient,
                                SqlEntityProjection<T> sqlProjection,
                                SqlFilterBuilder<F> filterBuilder) {
        this.sqlProjection   = sqlProjection;
        this.filterBuilder   = filterBuilder;
        this.sqlQueryBuilder = new SqlQueryBuilder();
        this.executor        = new SqlQueryExecutor<>(jdbcClient);
    }

    public RepositoryCustom(JdbcClient jdbcClient) {
        this.sqlProjection   = null;
        this.filterBuilder   = null;
        this.sqlQueryBuilder = new SqlQueryBuilder();
        this.executor        = new SqlQueryExecutor<>(jdbcClient);
    }

    // ── PROJECTION-BASED (requires full constructor) ──────────────────────────

    public List<T> findByFilter(F filter, Pageable pageable) {
        requireProjection();
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
        requireProjection();
        MapSqlParameterSource params = new MapSqlParameterSource();
        SqlCommand sql = SqlCommand.of(sqlQueryBuilder.count(
                sqlProjection.getCountSource(),
                filterBuilder.build(params, filter)
        ));
        return executor.count(sql, params);
    }

    public <C> Optional<T> find(SqlFilterBuilder<C> customApplier, C filter) {
        requireProjection();
        MapSqlParameterSource params = new MapSqlParameterSource();
        SqlCommand sql = SqlCommand.of(sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                customApplier.build(params, filter)
        ));
        return executor.findOne(sql, params, sqlProjection);
    }

    public Optional<T> findOne(String where, MapSqlParameterSource params) {
        requireProjection();
        SqlCommand sql = SqlCommand.of(sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                where
        ));
        return executor.findOne(sql, params, sqlProjection);
    }

    // ── SqlCommand-BASED ──────────────────────────────────────────────────────

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

    // ── PRIVATE ───────────────────────────────────────────────────────────────

    private void requireProjection() {
        if (sqlProjection == null)
            throw new UnsupportedOperationException(
                getClass().getSimpleName() + " was constructed without a projection");
    }

    private String pageableToSql(MapSqlParameterSource params, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) return "";
        params.addValue("limit",  pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        return "LIMIT :limit OFFSET :offset";
    }
}
