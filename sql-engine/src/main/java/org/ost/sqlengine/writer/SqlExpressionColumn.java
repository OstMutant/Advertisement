package org.ost.sqlengine.writer;

public record SqlExpressionColumn<E>(
        String column,
        String sqlExpression
) implements SqlWriteColumn<E> {}
