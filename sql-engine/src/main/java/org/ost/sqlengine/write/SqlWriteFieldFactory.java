package org.ost.sqlengine.write;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Function;

/**
 * Factory methods for {@link SqlWriteField} instances used in {@link SqlEntityWriter}.
 * <ul>
 *   <li>{@code field(column, extractor)} — mapped field; param name equals column name.</li>
 *   <li>{@code field(column, param, extractor)} — mapped field with a distinct param name.</li>
 *   <li>{@code fieldExpr(column, sqlExpression)} — fixed SQL expression, no named parameter.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlWriteFieldFactory {

    public static <T> SqlMappedField<T> field(String column, Function<T, Object> extractor) {
        return new SqlMappedField<>(column, extractor);
    }

    public static <T> SqlMappedField<T> field(String column, String param, Function<T, Object> extractor) {
        return new SqlMappedField<>(column, param, extractor);
    }

    public static <T> SqlExpressionField<T> fieldExpr(String column, String sqlExpression) {
        return new SqlExpressionField<>(column, sqlExpression);
    }
}
