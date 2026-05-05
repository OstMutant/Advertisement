package org.ost.sqlengine.writer;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class SqlEntityWriter<E> {

    private final String               table;
    private final List<SqlWriteColumn<E>> columns;

    private SqlEntityWriter(String table, List<SqlWriteColumn<E>> columns) {
        this.table   = table;
        this.columns = columns;
    }

    @SafeVarargs
    public static <E> SqlEntityWriter<E> of(String table, SqlWriteColumn<E>... columns) {
        return new SqlEntityWriter<>(table, Arrays.asList(columns));
    }

    public static <E> SqlMappedColumn<E> col(String column, Function<E, Object> extractor) {
        return new SqlMappedColumn<>(column, extractor);
    }

    public static <E> SqlMappedColumn<E> col(String column, String param, Function<E, Object> extractor) {
        return new SqlMappedColumn<>(column, param, extractor);
    }

    public static <E> SqlExpressionColumn<E> colExpr(String column, String sqlExpression) {
        return new SqlExpressionColumn<>(column, sqlExpression);
    }

    public String insertInto() {
        List<String> colNames = new ArrayList<>();
        List<String> valExprs = new ArrayList<>();
        for (SqlWriteColumn<E> c : columns) {
            switch (c) {
                case SqlMappedColumn<E> col -> {
                    colNames.add(col.column());
                    valExprs.add(":" + col.param());
                }
                case SqlExpressionColumn<E> expr -> {
                    colNames.add(expr.column());
                    valExprs.add(expr.sqlExpression());
                }
            }
        }
        return "INSERT INTO " + table +
               " (" + String.join(", ", colNames) + ")" +
               " VALUES (" + String.join(", ", valExprs) + ")";
    }

    public String updateWhere(String where) {
        List<String> setClauses = new ArrayList<>();
        for (SqlWriteColumn<E> c : columns) {
            switch (c) {
                case SqlMappedColumn<E> col ->
                        setClauses.add(col.column() + " = :" + col.param());
                case SqlExpressionColumn<E> expr ->
                        setClauses.add(expr.column() + " = " + expr.sqlExpression());
            }
        }
        return "UPDATE " + table + " SET " + String.join(", ", setClauses) + " WHERE " + where;
    }

    public MapSqlParameterSource params(E entity) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (SqlWriteColumn<E> c : columns) {
            switch (c) {
                case SqlMappedColumn<E> col ->
                        params.addValue(col.param(), col.extractor().apply(entity));
                case SqlExpressionColumn<E> expr -> {
                    // SQL expression has no named parameter — skip
                }
            }
        }
        return params;
    }
}
