package org.ost.sqlengine.write;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a dynamic UPDATE SQL string and the corresponding named parameters for a given source object.
 * Constructed once per descriptor via {@code SqlEntityWriter.of(table, fields...)} and reused
 * across requests.
 *
 * @param <T> the source type (entity, DTO, or any object fields are extracted from)
 */
public class SqlEntityWriter<T> {

    private final String table;
    private final List<SqlWriteField<T>> fields;

    private SqlEntityWriter(String table, List<SqlWriteField<T>> fields) {
        this.table  = table;
        this.fields = fields;
    }

    @SafeVarargs
    public static <T> SqlEntityWriter<T> of(String table, SqlWriteField<T>... fields) {
        return new SqlEntityWriter<>(table, Arrays.asList(fields));
    }

    public String updateWhere(String where) {
        String set = fields.stream().map(SqlWriteField::toSetClause).collect(Collectors.joining(", "));
        return "UPDATE " + table + " SET " + set + " WHERE " + where;
    }

    public MapSqlParameterSource params(T source) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        fields.forEach(f -> f.applyTo(source, params));
        return params;
    }
}
