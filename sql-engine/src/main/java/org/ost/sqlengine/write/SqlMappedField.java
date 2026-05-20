package org.ost.sqlengine.write;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.function.Function;

/**
 * A {@link SqlWriteField} that extracts a value from the source object via {@code extractor}
 * and binds it to a named SQL parameter. When {@code column} and {@code param} differ,
 * use the three-arg constructor; otherwise the two-arg shorthand reuses {@code column} as the param name.
 *
 * @param <T> the source type
 */
public record SqlMappedField<T>(
        String column,
        String param,
        Function<T, Object> extractor
) implements SqlWriteField<T> {

    public SqlMappedField(String column, Function<T, Object> extractor) {
        this(column, column, extractor);
    }

    @Override public String toSetClause() { return column + " = :" + param; }
    @Override public void applyTo(T source, MapSqlParameterSource params) {
        params.addValue(param, extractor.apply(source));
    }
}
