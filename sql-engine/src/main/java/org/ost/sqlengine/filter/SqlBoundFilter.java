package org.ost.sqlengine.filter;

import org.ost.sqlengine.projection.SqlField;
import org.ost.sqlengine.projection.SqlSelectField;

import java.util.function.BiFunction;

public record SqlBoundFilter<F, R>(
        String filterProperty,
        SqlField sqlField,
        BiFunction<SqlFilterMapping, F, SqlCondition<R>> conditionFunction
) implements SqlFilterBinding<F, R> {

    public static <F1, R1> SqlBoundFilter<F1, R1> of(
            String filterField,
            SqlSelectField<?> sqlField,
            BiFunction<SqlFilterMapping, F1, SqlCondition<R1>> conditionFunction
    ) {
        return new SqlBoundFilter<>(filterField, sqlField, conditionFunction);
    }

    @Override
    public String sqlExpression() {
        return sqlField.sqlExpression();
    }

    @Override
    public SqlCondition<R> getCondition(F value) {
        return conditionFunction.apply(this, value);
    }
}
