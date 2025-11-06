package org.ost.advertisement.repository.query.filter;

import java.util.function.BiFunction;
import org.ost.advertisement.repository.query.meta.SqlFieldDefinition;
import org.ost.advertisement.repository.query.meta.SqlFieldProjection;

public record SimpleFilterRelation<F, R>(
	String filterProperty,
	SqlFieldProjection sqlFieldDefinition,
	BiFunction<FilterProjection, F, Condition<R>> conditionFunction
) implements FilterRelation<F, R> {

	public static <F1, R1> SimpleFilterRelation<F1, R1> of(
		String filterField,
		SqlFieldDefinition<?> sqlFieldDefinition,
		BiFunction<FilterProjection, F1, Condition<R1>> conditionFunction
	) {
		return new SimpleFilterRelation<>(filterField, sqlFieldDefinition, conditionFunction);
	}

	@Override
	public String sqlExpression() {
		return sqlFieldDefinition.sqlExpression();
	}

	@Override
	public Condition<R> getCondition(F value) {
		return conditionFunction.apply(this, value);
	}

}
