package org.ost.advertisement.repository;

import static java.util.Optional.ofNullable;

import java.util.List;
import java.util.Optional;
import org.ost.advertisement.repository.query.exec.RepositoryExecutor;
import org.ost.advertisement.repository.query.filter.FilterApplier;
import org.ost.advertisement.repository.query.mapping.SqlProjection;
import org.ost.advertisement.repository.query.sql.SqlQueryBuilder;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class RepositoryCustom<T, F> {

	protected final SqlProjection<T> sqlProjection;
	protected final FilterApplier<F> filterApplier;
	protected final SqlQueryBuilder sqlQueryBuilder;
	protected final RepositoryExecutor<T> executor;

	protected RepositoryCustom(NamedParameterJdbcTemplate jdbc, SqlProjection<T> sqlProjection,
							   FilterApplier<F> filterApplier) {
		this.sqlProjection = sqlProjection;
		this.filterApplier = filterApplier;
		this.sqlQueryBuilder = new SqlQueryBuilder();
		this.executor = new RepositoryExecutor<>(jdbc);
	}

	public List<T> findByFilter(F filter, Pageable pageable) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = sqlQueryBuilder.select(
			sqlProjection.getSelectClause(),
			sqlProjection.getSqlSource(),
			filterApplier.apply(params, filter),
			sqlProjection.getOrderByClause(pageable.getSort()),
			pageableToSql(params, pageable)
		);
		return executor.findAll(sql, params, sqlProjection);
	}

	public Long countByFilter(F filter) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = sqlQueryBuilder.count(
			sqlProjection.getSqlSource(),
			filterApplier.apply(params, filter)
		);
		return executor.count(sql, params);
	}

	public <C> Optional<T> find(FilterApplier<C> customApplier, C filter) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = sqlQueryBuilder.select(
			sqlProjection.getSelectClause(),
			sqlProjection.getSqlSource(),
			customApplier.apply(params, filter)
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
