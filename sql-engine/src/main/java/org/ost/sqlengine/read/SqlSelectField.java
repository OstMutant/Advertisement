package org.ost.sqlengine.read;

import lombok.Builder;
import lombok.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Describes a single SELECT column: the SQL expression (e.g. {@code "a.title"}),
 * the alias used in the SELECT clause and ResultSet, and the typed extractor that reads
 * the value back from a {@link java.sql.ResultSet}.
 * Create instances via {@link SqlSelectFieldFactory} factory methods.
 *
 * @param <T> the Java type this field maps to
 */
@Builder
public record SqlSelectField<T>(
        @NonNull String sqlExpression,
        @NonNull String alias,
        @NonNull SqlFieldReader<T> extractor
) implements SqlField {

    public SqlSelectField {
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
