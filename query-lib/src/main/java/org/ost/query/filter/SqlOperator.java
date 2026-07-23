package org.ost.query.filter;

/**
 * SQL comparison operators used by {@link SqlCondition} to format WHERE clause fragments.
 * Each variant holds a printf-style template: {@code %s} is the SQL expression, {@code :%s} the named param.
 */
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
enum SqlOperator {
    EQUALS          ("%s = :%s"),
    LESS_OR_EQUAL   ("%s <= :%s"),
    GREATER_OR_EQUAL("%s >= :%s"),
    LIKE_IGNORE_CASE("%s ILIKE :%s ESCAPE '\\'"),
    IN              ("%s IN (:%s)"),
    ANY_OF          ("%s = ANY(:%s)");

    private final String template;

    public String formatClause(String sqlExpression, String filterProperty) {
        return template.formatted(sqlExpression, filterProperty);
    }
}
