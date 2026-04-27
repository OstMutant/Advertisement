package org.ost.sqlengine.writer;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlEntityWriter<E> {

    private final List<SqlColumnDefinition<E>> columns;

    @SafeVarargs
    public static <E> SqlEntityWriter<E> of(SqlColumnDefinition<E>... columns) {
        return new SqlEntityWriter<>(Arrays.asList(columns));
    }

    public static <E> SqlColumnDefinition<E> col(String column, Function<E, Object> extractor) {
        return new SqlColumnDefinition<>(column, extractor);
    }

    public String insertInto(String table) {
        String cols = columns.stream().map(SqlColumnDefinition::column).collect(Collectors.joining(", "));
        String vals = columns.stream().map(c -> ":" + c.column()).collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ")";
    }

    public String updateWhere(String table, String where) {
        return buildUpdate(table, null, where);
    }

    public String updateWhere(String table, String extraSet, String where) {
        return buildUpdate(table, extraSet, where);
    }

    public MapSqlParameterSource params(E entity) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        columns.forEach(c -> c.bind(params, entity));
        return params;
    }

    private String buildUpdate(String table, String extraSet, String where) {
        String sets = columns.stream()
                .map(c -> c.column() + " = :" + c.column())
                .collect(Collectors.joining(", "));
        String fullSet = extraSet != null ? sets + ", " + extraSet : sets;
        return "UPDATE " + table + " SET " + fullSet + " WHERE " + where;
    }
}
