package org.ost.sqlengine.writer;

import java.util.function.Function;

public record SqlColumnDefinition<E>(
        String column,
        String param,
        Function<E, Object> extractor
) implements SqlWriteColumn<E> {

    public SqlColumnDefinition(String column, Function<E, Object> extractor) {
        this(column, column, extractor);
    }
}
