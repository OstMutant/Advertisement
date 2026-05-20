package org.ost.sqlengine.write;

/**
 * A {@link SqlWriteField} that emits a fixed SQL expression (e.g. {@code NOW()}, {@code DEFAULT})
 * instead of a named parameter. Contributes to the SET clause but adds nothing to the parameter map.
 *
 * @param <T> the source type (unused at runtime; kept for type-safe use with {@link SqlEntityWriter})
 */
public record SqlExpressionField<T>(
        String column,
        String sqlExpression
) implements SqlWriteField<T> {

    @Override public String toSetClause() { return column + " = " + sqlExpression; }
}
