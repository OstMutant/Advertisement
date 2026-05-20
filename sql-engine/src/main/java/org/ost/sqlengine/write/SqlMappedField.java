package org.ost.sqlengine.write;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.function.Function;

/**
 * A {@link SqlWriteField} that extracts a value from the source object via {@code extractor}
 * and binds it under the column name as a named SQL parameter.
 *
 * @param <T> the source type
 */
public record SqlMappedField<T>(
        String column,
        Function<T, Object> extractor
) implements SqlWriteField<T> {

    @Override public String toSetClause() { return column + " = :" + column; }
    @Override public void applyTo(T source, MapSqlParameterSource params) {
        params.addValue(column, extractor.apply(source));
    }
}
