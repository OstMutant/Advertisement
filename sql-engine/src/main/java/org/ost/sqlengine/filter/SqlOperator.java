package org.ost.sqlengine.filter;

public enum SqlOperator {
    EQUALS          ("%s = :%s"),
    LESS_OR_EQUAL   ("%s <= :%s"),
    GREATER_OR_EQUAL("%s >= :%s"),
    LIKE_IGNORE_CASE("%s ILIKE :%s"),
    IN              ("%s IN (:%s)");

    private final String template;

    SqlOperator(String template) { this.template = template; }

    public String formatClause(String sqlExpression, String filterProperty) {
        return template.formatted(sqlExpression, filterProperty);
    }
}
