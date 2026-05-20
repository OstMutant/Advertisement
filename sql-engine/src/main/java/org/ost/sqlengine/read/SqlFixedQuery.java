package org.ost.sqlengine.read;

import org.ost.sqlengine.common.SqlDescriptorField;

import java.util.List;

/**
 * Projection that owns its complete, hand-written SQL query.
 * Use for structurally complex queries (CTEs, UNION ALL, self-joins) that cannot be
 * assembled dynamically by {@link org.ost.sqlengine.FilterableRepository}.
 *
 * <p>Subclasses declare {@link SqlDescriptorField} constants, implement {@code mapRow()},
 * and provide the full SQL via {@code querySql()}.</p>
 */
public abstract class SqlFixedQuery<T> extends SqlBaseProjection<T> {

    protected SqlFixedQuery(List<SqlDescriptorField<?>> items) {
        super(items);
    }

    public abstract String querySql();
}
