package org.ost.sqlengine.common;

import lombok.Builder;
import lombok.NonNull;
import org.ost.sqlengine.read.SqlFieldReader;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Describes a single table column used on both sides of a descriptor:
 * the SQL expression (e.g. {@code "a.title"}), the alias used in SELECT and ResultSet,
 * and the typed extractor that reads the value from a {@link ResultSet}.
 *
 * <p>On the read side it participates in SELECT projections ({@code SqlEntityProjection},
 * {@code SqlFixedQuery}). On the write side its {@link #columnName()} drives
 * {@code SqlWriteFieldFactory.field(SqlDescriptorField, extractor)}.
 *
 * <p>Create instances via {@link SqlDescriptorFieldFactory} factory methods.
 *
 * @param <T> the Java type this field maps to
 */
@Builder
public record SqlDescriptorField<T>(
        @NonNull String sqlExpression,
        @NonNull String alias,
        @NonNull SqlFieldReader<T> extractor
) implements SqlField {

    public SqlDescriptorField {
        if (sqlExpression.isBlank()) {
            throw new IllegalArgumentException("SQL expression must not be blank");
        }
        if (alias.isBlank()) {
            throw new IllegalArgumentException("Alias must not be blank");
        }
    }

    public T extract(ResultSet rs) throws SQLException {
        return extractor.apply(rs, alias);
    }

    public String columnName() {
        if (sqlExpression.contains("(") || sqlExpression.contains(" ")) {
            throw new UnsupportedOperationException(
                "columnName() supports only simple column expressions (e.g. 'a.title'), " +
                "not complex SQL: " + sqlExpression
            );
        }
        int dot = sqlExpression.indexOf('.');
        return dot >= 0 ? sqlExpression.substring(dot + 1) : sqlExpression;
    }
}
