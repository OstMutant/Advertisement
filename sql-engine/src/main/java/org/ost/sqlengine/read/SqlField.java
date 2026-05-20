package org.ost.sqlengine.read;

/**
 * Minimal contract shared by read-side field types: a SQL expression and an alias.
 * Used by {@link SqlBoundFilter} to source the SQL expression from the same field
 * declaration that drives the SELECT clause, keeping the two in sync.
 */
public interface SqlField {

    String sqlExpression();

    String alias();
}
