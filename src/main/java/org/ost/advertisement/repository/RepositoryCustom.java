package org.ost.advertisement.repository;

import static java.util.Optional.ofNullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.ost.advertisement.meta.fields.SqlDtoFieldDefinition;
import org.ost.advertisement.repository.query.filter.FilterApplier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class RepositoryCustom<T, F> {

	protected final NamedParameterJdbcTemplate jdbc;
	protected final FieldRelations<T> fieldRelations;
	protected final FilterApplier<F> filterApplier;

	protected RepositoryCustom(NamedParameterJdbcTemplate jdbc, FieldRelations<T> fieldRelations,
							   FilterApplier<F> filterApplier) {
		this.jdbc = jdbc;
		this.fieldRelations = fieldRelations;
		this.filterApplier = filterApplier;
	}

	public List<T> findByFilter(F filter, Pageable pageable) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = prepareSelectTemplate(
			fieldRelations.sourceToSql(),
			fieldRelations.fieldsToSql(),
			filterApplier.apply(params, filter),
			fieldRelations.sortToSql(pageable.getSort()),
			pageableToSql(params, pageable)
		);
		return jdbc.query(sql, params, fieldRelations);
	}

	public Long countByFilter(F filter) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = prepareSelectTemplate(
			fieldRelations.sourceToSql(),
			"COUNT(*)",
			filterApplier.apply(params, filter),
			null,
			null
		);
		return jdbc.queryForObject(sql, params, Long.class);
	}

	public <C> Optional<T> find(FilterApplier<C> customApplier, C filter) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = prepareSelectTemplate(
			fieldRelations.sourceToSql(),
			fieldRelations.fieldsToSql(),
			customApplier.apply(params, filter),
			null,
			null
		);
		return jdbc.query(sql, params, fieldRelations).stream().findFirst();
	}

	private String prepareSelectTemplate(String source, String fields, String where, String sort, String limit) {
		return Stream.of(
				"SELECT " + ofNullable(fields).filter(StringUtils::isNotBlank).orElse("*"),
				"FROM " + source,
				prependIfNotBlank(where, "WHERE "),
				prependIfNotBlank(sort, ""),
				prependIfNotBlank(limit, "")
			)
			.filter(StringUtils::isNotBlank)
			.collect(Collectors.joining(" "));
	}

	private String prependIfNotBlank(String part, String prefix) {
		return StringUtils.isNotBlank(part) ? prefix + part : "";
	}

	protected String pageableToSql(MapSqlParameterSource params, Pageable pageable) {
		return ofNullable(pageable)
			.filter(p -> !p.isUnpaged())
			.map(p -> {
				params.addValue("limit", p.getPageSize());
				params.addValue("offset", p.getOffset());
				return "LIMIT :limit OFFSET :offset";
			})
			.orElse("");
	}

	public abstract static class FieldRelations<T> implements RowMapper<T> {

		private final Map<String, String> dtoToSqlRelations;
		private final String sqlSource;

		protected FieldRelations(SqlDtoFieldDefinition<?>[] items, String sqlSource) {
			this.dtoToSqlRelations = Stream.of(items)
				.collect(Collectors.toMap(
					SqlDtoFieldDefinition::getDtoField,
					SqlDtoFieldDefinition::getSqlField,
					(existing, replacement) -> existing,
					HashMap::new
				));
			this.sqlSource = sqlSource;
		}

		public String sourceToSql() {
			return sqlSource;
		}

		public String fieldsToSql() {
			return dtoToSqlRelations.entrySet().stream()
				.map(v -> v.getValue() + " AS " + v.getKey())
				.collect(Collectors.joining(", "));
		}

		public String sortToSql(Sort sort) {
			String orderByFragment = ofNullable(sort)
				.filter(s -> !s.isEmpty())
				.map(Sort::stream)
				.orElseGet(Stream::empty)
				.map(order -> ofNullable(dtoToSqlRelations.get(order.getProperty()))
					.map(col -> col + " " + order.getDirection().name())
					.orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.joining(", "));
			return StringUtils.isBlank(orderByFragment) ? "" : " ORDER BY " + orderByFragment;
		}
	}
}
