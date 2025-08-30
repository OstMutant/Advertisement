package org.ost.advertisement.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import org.ost.advertisement.repository.RepositoryCustom.FieldRelations.SqlDtoFieldRelation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class RepositoryCustom<T, F> {

	protected final NamedParameterJdbcTemplate jdbc;
	protected final FieldRelations<T> fieldRelations;
	protected final FieldConditionsRules<F> fieldConditionsRules;

	protected RepositoryCustom(NamedParameterJdbcTemplate jdbc, FieldRelations<T> fieldRelations,
							   FieldConditionsRules<F> fieldConditionsRules) {
		this.jdbc = jdbc;
		this.fieldRelations = fieldRelations;
		this.fieldConditionsRules = fieldConditionsRules;
	}

	public static Timestamp toTimestamp(Instant instant) {
		return instant != null ? Timestamp.from(instant) : null;
	}

	public static Instant toInstant(Timestamp ts) {
		return ts != null ? ts.toInstant() : null;
	}

	public List<T> findByFilter(F filter, Pageable pageable) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		StringBuilder sql = prepareSelectTemplate(fieldRelations.sourceToSql(), fieldRelations.fieldsToSql(),
			fieldConditionsRules.apply(params, filter), fieldRelations.sortToSql(pageable.getSort()),
			pageableToSql(params, pageable));
		return jdbc.query(sql.toString(), params, fieldRelations);
	}

	public Long countByFilter(F filter) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		StringBuilder sql = prepareSelectTemplate(fieldRelations.sourceToSql(), "COUNT(*)",
			fieldConditionsRules.apply(params, filter), null, null);
		return jdbc.queryForObject(sql.toString(), params, Long.class);
	}

	public <C> Optional<T> find(FieldConditionsRules<C> fieldConditionsRules, C filter) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		StringBuilder sql = prepareSelectTemplate(fieldRelations.sourceToSql(), fieldRelations.fieldsToSql(),
			fieldConditionsRules.apply(params, filter), null, null);
		return jdbc.query(sql.toString(), params, fieldRelations).stream().findFirst();
	}

	private StringBuilder prepareSelectTemplate(String source, String fields, String conditions, String sorting,
												String pagination) {
		return new StringBuilder()
			.append("SELECT ")
			.append(fields == null || fields.isBlank() ? "*" : fields)
			.append(" FROM ")
			.append(source)
			.append(conditions == null || conditions.isBlank() ? "" : " WHERE " + conditions)
			.append(sorting == null || sorting.isBlank() ? "" : sorting)
			.append(pagination == null || pagination.isBlank() ? "" : pagination);
	}

	protected String pageableToSql(MapSqlParameterSource params, Pageable pageable) {
		String sql = " LIMIT :limit OFFSET :offset ";
		params.addValue("limit", pageable.getPageSize())
			.addValue("offset", pageable.getOffset());
		return sql;
	}

	public abstract static class FieldRelations<T> implements RowMapper<T> {

		public interface SqlDtoFieldRelation {

			String getSqlField();

			String getDtoField();
		}

		private final Map<String, String> dtoToSqlRelations;
		private final String sqlSource;

		protected FieldRelations(Set<? extends SqlDtoFieldRelation> items, String sqlSource) {
			if (Objects.isNull(items) || items.isEmpty()) {
				this.dtoToSqlRelations = Map.of();
			} else {
				this.dtoToSqlRelations = items.stream().collect(
					Collectors.toMap(SqlDtoFieldRelation::getDtoField, SqlDtoFieldRelation::getSqlField));
			}
			this.sqlSource = sqlSource;
		}

		public String sourceToSql() {
			return sqlSource;
		}

		public String fieldsToSql() {
			return dtoToSqlRelations.entrySet().stream().map(v -> v.getValue() + " AS " + v.getKey())
				.collect(Collectors.joining(", "));
		}

		public String sortToSql(Sort sort) {
			StringBuilder sql = new StringBuilder();
			if (sort.isSorted()) {
				sql.append(" ORDER BY ")
					.append(
						sort.stream().map(this::orderToSql).filter(Objects::nonNull)
							.collect(Collectors.joining(", "))
					);
			}
			return sql.toString();
		}

		public String resolve(String dtoField) {
			return dtoToSqlRelations.getOrDefault(dtoField, dtoField);
		}

		private String orderToSql(Order order) {
			String column = dtoToSqlRelations.get(order.getProperty());
			return column == null ? null : column + " " + order.getDirection().name();
		}
	}

	public static class FieldConditions<F> {

		@Getter
		private final F filter;
		private final List<String> sqlFragments = new ArrayList<>();
		private final Map<String, Object> parameters = new LinkedHashMap<>();

		public FieldConditions(F filter) {
			this.filter = filter;
		}

		public void update(String sqlFragment, String filterField, Object value) {
			sqlFragments.add(sqlFragment);
			parameters.put(filterField, value);
		}

		public String toSqlApplyingAnd() {
			return String.join(" AND ", sqlFragments);
		}

		public Map<String, Object> toParams() {
			return new HashMap<>(parameters);
		}
	}


	public abstract static class FieldConditionsRules<F> {

		public interface Relation {

			String getFilterField();

			SqlDtoFieldRelation getSqlDtoFieldRelation();
		}

		protected final Set<? extends Relation> relations;

		protected FieldConditionsRules(Set<? extends Relation> items) {
			relations = items == null ? Set.of() : items;
		}

		private <V, R> void applyConditions(boolean isApply, Relation relation, V value,
											BinaryOperator<String> sqlFunction,
											Function<V, R> parametersFunction,
											FieldConditions<F> fieldConditions) {
			if (isApply) {
				String filterField = relation.getFilterField();
				String sqlField = relation.getSqlDtoFieldRelation().getSqlField();
				fieldConditions.update(sqlFunction.apply(sqlField, filterField), filterField,
					parametersFunction.apply(value));
			}
		}

		public void like(Relation relation, String value, FieldConditions<F> fieldConditions) {
			applyConditions(value != null && !value.isBlank(), relation, value,
				(sqlField, param) -> sqlField + " ILIKE '%' || :" + param + " || '%'", v -> v, fieldConditions);
		}

		public void after(Relation relation, Instant value, FieldConditions<F> fieldConditions) {
			applyConditions(value != null, relation, value,
				(sqlField, param) -> sqlField + " >= :" + param, Timestamp::from, fieldConditions);
		}

		public void before(Relation relation, Instant value, FieldConditions<F> fieldConditions) {
			applyConditions(value != null, relation, value,
				(sqlField, param) -> sqlField + " <= :" + param, Timestamp::from, fieldConditions);
		}

		public void after(Relation relation, Long value, FieldConditions<F> fieldConditions) {
			applyConditions(value != null, relation, value,
				(sqlField, param) -> sqlField + " >= :" + param, Function.identity(), fieldConditions);
		}

		public void before(Relation relation, Long value, FieldConditions<F> fieldConditions) {
			applyConditions(value != null, relation, value,
				(sqlField, param) -> sqlField + " <= :" + param, Function.identity(), fieldConditions);
		}

		public void equalsTo(Relation relation, String value, FieldConditions<F> fieldConditions) {
			applyConditions(value != null, relation, value,
				(sqlField, param) -> sqlField + " = :" + param, Function.identity(), fieldConditions);
		}

		public abstract String apply(MapSqlParameterSource params, F filter);
	}
}
