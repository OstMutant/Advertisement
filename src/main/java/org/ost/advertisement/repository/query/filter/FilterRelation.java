package org.ost.advertisement.repository.query.filter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.function.Function;

public interface FilterRelation<F> {

	String getFilterField();

	String getSqlField();

	void applyConditions(F filter, FieldsConditions fc);

	default FieldsConditions like(String value, FieldsConditions fc) {
		return applyIfPresent(value, "ILIKE", v -> "%" + v + "%", fc);
	}

	default FieldsConditions equalsTo(String value, FieldsConditions fc) {
		return applyIfPresent(value, "=", Function.identity(), fc);
	}

	default FieldsConditions after(Instant value, FieldsConditions fc) {
		return applyIfPresent(value, ">=", Timestamp::from, fc);
	}

	default FieldsConditions before(Instant value, FieldsConditions fc) {
		return applyIfPresent(value, "<=", Timestamp::from, fc);
	}

	default FieldsConditions after(Long value, FieldsConditions fc) {
		return applyIfPresent(value, ">=", Function.identity(), fc);
	}

	default FieldsConditions before(Long value, FieldsConditions fc) {
		return applyIfPresent(value, "<=", Function.identity(), fc);
	}

	default <V, R> FieldsConditions applyIfPresent(V value, String op, Function<V, R> paramMapper,
												   FieldsConditions fc) {
		return value != null
			? fc.add(getSqlField() + " " + op + " :" + getFilterField(), getFilterField(), paramMapper.apply(value))
			: fc;
	}
}

