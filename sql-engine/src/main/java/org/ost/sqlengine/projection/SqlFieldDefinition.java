package org.ost.sqlengine.projection;

import lombok.Builder;
import lombok.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;

@Builder
public record SqlFieldDefinition<T>(
        @NonNull String sqlExpression,
        @NonNull String alias,
        @NonNull SqlFieldReader<T> extractor
) implements SqlFieldProjection {

    public SqlFieldDefinition {
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

