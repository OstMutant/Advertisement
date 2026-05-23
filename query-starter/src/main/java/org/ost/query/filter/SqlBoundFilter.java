package org.ost.sqlengine.filter;

import java.util.function.BiFunction;

public record SqlBoundFilter<F, R>(
        String filterProperty,
        String sqlExpression,
        BiFunction<SqlFilterMapping, F, SqlCondition<R>> conditionFunction
) implements SqlFilterBinding<F, R> {

    public static <F1, R1> SqlBoundFilter<F1, R1> of(
            String filterProperty,
            String sqlExpression,
            BiFunction<SqlFilterMapping, F1, SqlCondition<R1>> conditionFunction
    ) {
        return new SqlBoundFilter<>(filterProperty, sqlExpression, conditionFunction);
    }

    @Override
    public SqlCondition<R> getCondition(F value) {
        return conditionFunction.apply(this, value);
    }
}
