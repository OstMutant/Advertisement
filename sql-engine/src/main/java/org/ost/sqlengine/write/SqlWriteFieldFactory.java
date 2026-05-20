package org.ost.sqlengine.write;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.sqlengine.read.SqlSelectField;

import java.util.function.Function;

/**
 * Factory methods for {@link SqlWriteField} instances used in {@link SqlEntityWriter}.
 *
 * <p>Two overload families:
 * <ul>
 *   <li>{@code field(SqlSelectField, extractor)} / {@code fieldExpr(SqlSelectField, sql)} —
 *       preferred when a Read-side {@link SqlSelectField} constant exists in the same descriptor;
 *       column name is taken from {@link SqlSelectField#columnName()}, making Read and Write
 *       share a single source of truth.</li>
 *   <li>{@code field(String, extractor)} / {@code fieldExpr(String, sql)} —
 *       fallback for fields without a matching Read constant; accepts camelCase Java names
 *       (e.g. {@code Fields.*} from {@code @FieldNameConstants}) and converts them to
 *       snake_case automatically ({@code updatedAt} → {@code updated_at}).</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlWriteFieldFactory {

    public static <T> SqlMappedField<T> field(SqlSelectField<?> readField, Function<T, Object> extractor) {
        return new SqlMappedField<>(readField.columnName(), extractor);
    }

    public static <T> SqlExpressionField<T> fieldExpr(SqlSelectField<?> readField, String sqlExpression) {
        return new SqlExpressionField<>(readField.columnName(), sqlExpression);
    }

    public static <T> SqlMappedField<T> field(String javaFieldName, Function<T, Object> extractor) {
        return new SqlMappedField<>(toSnakeCase(javaFieldName), extractor);
    }

    public static <T> SqlMappedField<T> field(String javaFieldName, String param, Function<T, Object> extractor) {
        return new SqlMappedField<>(toSnakeCase(javaFieldName), param, extractor);
    }

    public static <T> SqlExpressionField<T> fieldExpr(String javaFieldName, String sqlExpression) {
        return new SqlExpressionField<>(toSnakeCase(javaFieldName), sqlExpression);
    }

    private static String toSnakeCase(String name) {
        return name.replaceAll("([A-Z])", "_$1").toLowerCase();
    }
}
