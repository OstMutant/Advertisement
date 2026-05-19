package org.ost.sqlengine.write;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SqlEntityWriter<E> {

    private final String table;
    private final List<SqlWriteField<E>> fields;

    private SqlEntityWriter(String table, List<SqlWriteField<E>> fields) {
        this.table  = table;
        this.fields = fields;
    }

    @SafeVarargs
    public static <E> SqlEntityWriter<E> of(String table, SqlWriteField<E>... fields) {
        return new SqlEntityWriter<>(table, Arrays.asList(fields));
    }

    public String updateWhere(String where) {
        List<String> setClauses = new ArrayList<>();
        for (SqlWriteField<E> f : fields) {
            switch (f) {
                case SqlMappedField<E>(var column, var param, _) ->
                        setClauses.add(column + " = :" + param);
                case SqlExpressionField<E>(var column, var sqlExpression) ->
                        setClauses.add(column + " = " + sqlExpression);
            }
        }
        return "UPDATE " + table + " SET " + String.join(", ", setClauses) + " WHERE " + where;
    }

    public MapSqlParameterSource params(E entity) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (SqlWriteField<E> f : fields) {
            switch (f) {
                case SqlMappedField<E>(_, var param, var extractor) ->
                        params.addValue(param, extractor.apply(entity));
                case SqlExpressionField<E> _ -> {
                    // SQL expression has no named parameter — skip
                }
            }
        }
        return params;
    }
}
