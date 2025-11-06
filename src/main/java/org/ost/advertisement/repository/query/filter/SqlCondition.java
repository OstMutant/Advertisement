package org.ost.advertisement.repository.query.filter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.function.Function;

public record SqlCondition<R>(String sqlExpression, String filterProperty, R value, String operation) {

	public static <R> SqlCondition<R> of(String sqlExpression, String filterProperty, R value, String operation) {
		return new SqlCondition<>(sqlExpression, filterProperty, value, operation);
	}

	public String getConditionClause() {
		return sqlExpression + " " + operation + " :" + filterProperty;
	}

	public static SqlCondition<String> like(FilterMapping filterMapping, String value) {
		return applyIfPresent(filterMapping, value, "ILIKE", v -> "%" + v + "%");
	}

	public static SqlCondition<String> equalsTo(FilterMapping filterMapping, String value) {
		return applyIfPresent(filterMapping, value, "=", Function.identity());
	}

	public static SqlCondition<Timestamp> after(FilterMapping filterMapping, Instant value) {
		return applyIfPresent(filterMapping, value, ">=", Timestamp::from);
	}

	public static SqlCondition<Timestamp> before(FilterMapping filterMapping, Instant value) {
		return applyIfPresent(filterMapping, value, "<=", Timestamp::from);
	}

	public static SqlCondition<Long> after(FilterMapping filterMapping, Long value) {
		return applyIfPresent(filterMapping, value, ">=", Function.identity());
	}

	public static SqlCondition<Long> before(FilterMapping filterMapping, Long value) {
		return applyIfPresent(filterMapping, value, "<=", Function.identity());
	}

	private static <I1, R1> SqlCondition<R1> applyIfPresent(FilterMapping filterMapping, I1 value,
															String operation,
															Function<I1, R1> valueMapper) {
		return value != null
			? SqlCondition.of(filterMapping.sqlExpression(), filterMapping.filterProperty(),
			valueMapper.apply(value), operation)
			: null;
	}
}
