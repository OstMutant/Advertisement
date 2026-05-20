package org.ost.sqlengine.filter;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class SqlFilterBuilder<F> {

    protected final List<SqlFilterBinding<F, ?>> bindings;

    protected SqlFilterBuilder(List<SqlFilterBinding<F, ?>> bindings) {
        this.bindings = List.copyOf(bindings);
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

    public Map<String, Object> toParams(List<SqlCondition<?>> sqlConditions) {
        return sqlConditions.stream().collect(Collectors.toMap(SqlCondition::filterProperty, SqlCondition::value));
    }
}
