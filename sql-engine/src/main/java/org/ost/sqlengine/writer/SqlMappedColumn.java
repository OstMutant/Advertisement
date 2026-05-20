package org.ost.sqlengine.writer;

import java.util.function.Function;

public record SqlMappedColumn<E>(
        String column,
        String param,
        Function<E, Object> extractor
) implements SqlWriteColumn<E> {

    public SqlMappedColumn(String column, Function<E, Object> extractor) {
        this(column, column, extractor);
    }
}
