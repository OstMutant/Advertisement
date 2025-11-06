package org.ost.advertisement.repository.query.filter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.function.Function;

public record Condition<R>(String sqlExpression, String filterProperty, R value, String operation) {

	public static <R> Condition<R> of(String sqlExpression, String filterProperty, R value, String operation) {
		return new Condition<>(sqlExpression, filterProperty, value, operation);
	}

	public String getConditionClause() {
		return sqlExpression + " " + operation + " :" + filterProperty;
	}

	public static Condition<String> like(FilterProjection filterProjection, String value) {
		return applyIfPresent(filterProjection, value, "ILIKE", v -> "%" + v + "%");
	}

	public static Condition<String> equalsTo(FilterProjection filterProjection, String value) {
		return applyIfPresent(filterProjection, value, "=", Function.identity());
	}

	public static Condition<Timestamp> after(FilterProjection filterProjection, Instant value) {
		return applyIfPresent(filterProjection, value, ">=", Timestamp::from);
	}

	public static Condition<Timestamp> before(FilterProjection filterProjection, Instant value) {
		return applyIfPresent(filterProjection, value, "<=", Timestamp::from);
	}

	public static Condition<Long> after(FilterProjection filterProjection, Long value) {
		return applyIfPresent(filterProjection, value, ">=", Function.identity());
	}

	public static Condition<Long> before(FilterProjection filterProjection, Long value) {
		return applyIfPresent(filterProjection, value, "<=", Function.identity());
	}

	private static <I1, R1> Condition<R1> applyIfPresent(FilterProjection filterProjection, I1 value,
														 String operation,
														 Function<I1, R1> valueMapper) {
		return value != null
			? Condition.of(filterProjection.sqlExpression(), filterProjection.filterProperty(),
			valueMapper.apply(value), operation)
			: null;
	}
}
