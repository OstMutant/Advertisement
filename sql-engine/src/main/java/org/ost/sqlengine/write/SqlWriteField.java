package org.ost.sqlengine.write;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Describes a single column in a dynamic UPDATE statement.
 * Two permitted implementations:
 * <ul>
 *   <li>{@link SqlMappedField} — binds a named parameter extracted from the source object.</li>
 *   <li>{@link SqlExpressionField} — emits a fixed SQL expression (e.g. {@code NOW()});
 *       contributes no named parameter.</li>
 * </ul>
 *
 * @param <T> the source type (load-bearing for type-safety at {@link SqlEntityWriter} usage sites)
 */
@SuppressWarnings("java:S2326") // T is load-bearing for type-safety at SqlEntityWriter<T> usage sites
public sealed interface SqlWriteField<T> permits SqlMappedField, SqlExpressionField {
    String toSetClause();
    default void applyTo(T source, MapSqlParameterSource params) {}
}
