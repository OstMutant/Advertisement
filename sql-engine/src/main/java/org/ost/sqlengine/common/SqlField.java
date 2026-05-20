package org.ost.sqlengine.common;

/**
 * Minimal contract shared by read-side field types: a SQL expression and an alias.
 * Used by {@link org.ost.sqlengine.filter.SqlBoundFilter} to source the SQL expression
 * from the same field declaration that drives the SELECT clause, keeping the two in sync.
 */
public interface SqlField {

    String sqlExpression();

    String alias();
}
