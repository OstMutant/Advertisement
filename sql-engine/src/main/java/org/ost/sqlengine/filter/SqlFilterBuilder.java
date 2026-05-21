package org.ost.sqlengine.filter;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Translates a filter DTO into a SQL WHERE fragment and populates the corresponding
 * named parameters. Each {@link SqlFilterBinding} in the binding list inspects the filter
 * and returns a {@link SqlCondition} (or {@code null} if the field is absent/empty).
 * Only non-null conditions are joined with {@code AND} and added to the params.
 *
 * @param <F> the filter DTO type
 */
public class SqlFilterBuilder<F> {

    protected final List<SqlFilterBinding<F, ?>> bindings;

    public SqlFilterBuilder(List<SqlFilterBinding<F, ?>> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    public String build(MapSqlParameterSource params, F filter, String prefix) {
        String dynamic = build(params, filter);
        return dynamic.isBlank() ? "" : prefix + dynamic;
    }

    public String build(MapSqlParameterSource params, F filter) {
        List<SqlCondition<?>> sqlConditions = bindings.stream()
                .<SqlCondition<?>>map(r -> r.getCondition(filter))
                .filter(Objects::nonNull)
                .toList();
        toParams(sqlConditions).forEach(params::addValue);
        return toSql(sqlConditions);
    }

    private String toSql(List<SqlCondition<?>> sqlConditions) {
        return sqlConditions.stream()
                .map(SqlCondition::getConditionClause)
                .collect(Collectors.joining(" AND "));
    }

    private Map<String, Object> toParams(List<SqlCondition<?>> sqlConditions) {
        return sqlConditions.stream().collect(Collectors.toMap(SqlCondition::filterProperty, SqlCondition::value));
    }
}
