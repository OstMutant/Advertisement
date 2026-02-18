package org.ost.advertisement.repository.query.projection;

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
}

