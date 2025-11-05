package org.ost.advertisement.repository;

import static java.util.Optional.ofNullable;

import java.util.Optional;
import org.ost.advertisement.repository.query.exec.RepositoryExecutor;
import org.ost.advertisement.repository.query.filter.FilterApplier;
import org.ost.advertisement.repository.query.mapping.FieldRelations;
import org.ost.advertisement.repository.query.sql.SqlQueryBuilder;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class RepositoryCustom<T, F> {

	protected final FieldRelations<T> fieldRelations;
	protected final FilterApplier<F> filterApplier;
	protected final SqlQueryBuilder sqlBuilder;
	protected final RepositoryExecutor<T> executor;

	protected RepositoryCustom(NamedParameterJdbcTemplate jdbc, FieldRelations<T> fieldRelations,
							   FilterApplier<F> filterApplier) {
		this.fieldRelations = fieldRelations;
		this.filterApplier = filterApplier;
		this.sqlBuilder = new SqlQueryBuilder();
		this.executor = new RepositoryExecutor<>(jdbc, fieldRelations);
	}

	public java.util.List<T> findByFilter(F filter, Pageable pageable) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = sqlBuilder.select(
			fieldRelations.getAliasedFieldsString(),
			fieldRelations.getSqlSource(),
			filterApplier.apply(params, filter),
			fieldRelations.getSqlOrderByClause(pageable.getSort()),
			pageableToSql(params, pageable)
		);
		return executor.executeQuery(sql, params);
	}

	public Long countByFilter(F filter) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = sqlBuilder.count(
			fieldRelations.getSqlSource(),
			filterApplier.apply(params, filter)
		);
		return executor.executeCount(sql, params);
	}

	public <C> Optional<T> find(FilterApplier<C> customApplier, C filter) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = sqlBuilder.select(
			fieldRelations.getAliasedFieldsString(),
			fieldRelations.getSqlSource(),
			customApplier.apply(params, filter)
		);
		return executor.executeSingle(sql, params);
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
