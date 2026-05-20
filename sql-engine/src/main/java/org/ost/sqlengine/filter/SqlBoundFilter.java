package org.ost.sqlengine.filter;

import org.ost.sqlengine.read.SqlField;
import org.ost.sqlengine.read.SqlSelectField;

import java.util.function.BiFunction;

/**
 * Concrete {@link SqlFilterBinding} that binds a filter DTO field to a {@link SqlSelectField}
 * via a user-supplied condition function. The SQL expression is taken from the field so
 * it stays in sync with the SELECT clause automatically.
 *
 * @param <F> the filter DTO type
 * @param <R> the parameter value type
 */
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
