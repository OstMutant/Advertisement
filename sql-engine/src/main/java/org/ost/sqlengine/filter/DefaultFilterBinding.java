package org.ost.sqlengine.filter;

import org.ost.sqlengine.projection.SqlFieldDefinition;
import org.ost.sqlengine.projection.SqlFieldProjection;

import java.util.function.BiFunction;

public record DefaultFilterBinding<F, R>(
        String filterProperty,
        SqlFieldProjection sqlFieldDefinition,
        BiFunction<FilterMapping, F, SqlCondition<R>> conditionFunction
) implements FilterBinding<F, R> {

    public static <F1, R1> DefaultFilterBinding<F1, R1> of(
            String filterField,
            SqlFieldDefinition<?> sqlFieldDefinition,
            BiFunction<FilterMapping, F1, SqlCondition<R1>> conditionFunction
    ) {
        return new DefaultFilterBinding<>(filterField, sqlFieldDefinition, conditionFunction);
    }

    @Override
    public String sqlExpression() {
        return sqlFieldDefinition.sqlExpression();
    }

    @Override
    public SqlCondition<R> getCondition(F value) {
        return conditionFunction.apply(this, value);
    }

}
