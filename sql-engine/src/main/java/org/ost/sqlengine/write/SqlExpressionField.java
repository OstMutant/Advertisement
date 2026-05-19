package org.ost.sqlengine.write;

public record SqlExpressionField<E>(
        String column,
        String sqlExpression
) implements SqlWriteField<E> {}
