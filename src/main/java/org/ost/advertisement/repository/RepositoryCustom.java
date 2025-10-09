package org.ost.advertisement.repository;

import static java.util.Optional.ofNullable;

import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.ost.advertisement.meta.fields.SqlDtoFieldRelation;
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

	public <C> Optional<T> find(FilterApplier<C> filterApplier, C filter) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String sql = prepareSelectTemplate(
			fieldRelations.sourceToSql(),
			fieldRelations.fieldsToSql(),
			filterApplier.apply(params, filter),
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
				params.addValue("limit", p.getPageSize())
					.addValue("offset", p.getOffset());
				return " LIMIT :limit OFFSET :offset ";
			})
			.orElse("");
	}

	public abstract static class FieldRelations<T> implements RowMapper<T> {

		private final Map<String, String> dtoToSqlRelations;
		private final String sqlSource;

		protected FieldRelations(SqlDtoFieldRelation[] items, String sqlSource) {
			this.dtoToSqlRelations = Stream.of(items)
				.collect(Collectors.toMap(
					SqlDtoFieldRelation::getDtoField,
					SqlDtoFieldRelation::getSqlField,
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

		public static Timestamp toTimestamp(Instant instant) {
			return instant != null ? Timestamp.from(instant) : null;
		}

		public static Instant toInstant(Timestamp ts) {
			return ts != null ? ts.toInstant() : null;
		}
	}

	@NoArgsConstructor
	public static class FieldsConditions {

		public record Condition(String sql, String param, Object value) {

		}

		private final List<Condition> conditions = new ArrayList<>();

		public FieldsConditions add(String sql, String param, Object value) {
			conditions.add(new Condition(sql, param, value));
			return this;
		}

		public String toSqlApplyingAnd() {
			return toSql(" AND ");
		}

		public Map<String, Object> toParams() {
			return conditions.stream()
				.collect(Collectors.toMap(
					Condition::param,
					Condition::value,
					(existing, replacement) -> replacement
				));
		}

		public String toSql(String joiner) {
			return conditions.stream().map(Condition::sql).collect(Collectors.joining(" " + joiner + " "));
		}
	}

	public interface FilterRelation<F> {

		String getFilterField();

		SqlDtoFieldRelation getSqlDtoFieldRelation();

		void applyConditions(F filter, FieldsConditions fieldsConditions);

		default FieldsConditions like(String value, FieldsConditions fieldsConditions) {
			return applyConditions(value != null && !value.isBlank(), value,
				(sqlField, param) -> sqlField + " ILIKE :" + param, v -> "%" + v + "%", fieldsConditions);
		}

		default FieldsConditions after(Instant value, FieldsConditions fieldsConditions) {
			return condition(value, ">=", Timestamp::from, fieldsConditions);
		}

		default FieldsConditions before(Instant value, FieldsConditions fieldsConditions) {
			return condition(value, "<=", Timestamp::from, fieldsConditions);
		}

		default FieldsConditions after(Long value, FieldsConditions fieldsConditions) {
			return condition(value, ">=", Function.identity(), fieldsConditions);
		}

		default FieldsConditions before(Long value, FieldsConditions fieldsConditions) {
			return condition(value, "<=", Function.identity(), fieldsConditions);
		}

		default FieldsConditions equalsTo(String value, FieldsConditions fieldsConditions) {
			return condition(value, "=", Function.identity(), fieldsConditions);
		}

		default <V, R> FieldsConditions condition(V v, String op, Function<V, R> f, FieldsConditions fc) {
			return applyConditions(v != null, v, (sql, param) -> sql + " " + op + " :" + param, f, fc);
		}

		default <V, R> FieldsConditions applyConditions(boolean isApply, V value,
														BinaryOperator<String> sqlFunction,
														Function<V, R> parametersFunction,
														FieldsConditions fieldsConditions) {
			return isApply ? fieldsConditions.add(
				sqlFunction.apply(getSqlDtoFieldRelation().getSqlField(), getFilterField()), getFilterField(),
				parametersFunction.apply(value)) : fieldsConditions;
		}
	}

	public abstract static class FilterApplier<F> {

		protected static <F> FilterRelation<F> of(
			String filterField,
			SqlDtoFieldRelation sqlDtoFieldRelation,
			FilterApplierFunction<F, FieldsConditions, FilterRelation<F>, FieldsConditions> fn
		) {
			return new FilterRelation<>() {
				@Override
				public String getFilterField() {
					return filterField;
				}

				@Override
				public SqlDtoFieldRelation getSqlDtoFieldRelation() {
					return sqlDtoFieldRelation;
				}

				@Override
				public void applyConditions(F filter, FieldsConditions fc) {
					fn.apply(filter, fc, this);
				}
			};
		}

		@FunctionalInterface
		protected interface FilterApplierFunction<A, B, C, R> {

			R apply(A a, B b, C c);
		}

		protected final List<FilterRelation<F>> relations = new ArrayList<>();

		protected String applyRelations(MapSqlParameterSource params, @NotNull F filter) {
			FieldsConditions fc = new FieldsConditions();
			relations.forEach(r -> r.applyConditions(filter, fc));
			fc.toParams().forEach(params::addValue);
			return fc.toSqlApplyingAnd();
		}

		public abstract String apply(MapSqlParameterSource params, @NotNull F filter);
	}
}

