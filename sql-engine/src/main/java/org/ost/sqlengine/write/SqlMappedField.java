package org.ost.sqlengine.write;

import java.util.function.Function;

public record SqlMappedField<E>(
        String column,
        String param,
        Function<E, Object> extractor
) implements SqlWriteField<E> {

    public SqlMappedField(String column, Function<E, Object> extractor) {
        this(column, column, extractor);
    }
}
