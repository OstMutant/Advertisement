package org.ost.sqlengine.filter;

/**
 * Pairs a filter parameter name ({@code filterProperty}, used as the SQL named parameter)
 * with the SQL expression it applies to. Implemented by both {@link SqlBoundFilter}
 * and inline lambdas passed to {@link SqlCondition} factory methods.
 */
public interface SqlFilterMapping {

    String filterProperty();

    String sqlExpression();
}
