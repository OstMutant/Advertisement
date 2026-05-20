package org.ost.sqlengine.filter;

/**
 * Maps one field of a filter DTO to a {@link SqlCondition}.
 * Returns {@code null} when the field is absent so the condition is skipped in WHERE.
 *
 * @param <F> the filter DTO type
 * @param <R> the parameter value type
 */
public interface SqlFilterBinding<F, R> extends SqlFilterMapping {

    SqlCondition<R> getCondition(F value);
}
