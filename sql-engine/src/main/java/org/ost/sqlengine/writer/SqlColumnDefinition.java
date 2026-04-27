package org.ost.sqlengine.writer;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.function.Function;

public record SqlColumnDefinition<E>(String column, Function<E, Object> extractor) {

    public void bind(MapSqlParameterSource params, E entity) {
        params.addValue(column, extractor.apply(entity));
    }
}
