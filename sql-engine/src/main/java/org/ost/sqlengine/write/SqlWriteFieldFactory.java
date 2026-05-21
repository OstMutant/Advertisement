package org.ost.sqlengine.write;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.sqlengine.common.SqlDescriptorField;

import java.util.function.Function;

import static org.ost.sqlengine.common.SqlNamingUtil.toSnakeCase;

/**
 * Factory methods for {@link SqlWriteField} instances used in {@link SqlEntityWriter}.
 *
 * <p>Two overload families:
 * <ul>
 *   <li>{@code field(SqlDescriptorField, extractor)} / {@code fieldExpr(SqlDescriptorField, sql)} —
 *       preferred when a {@link SqlDescriptorField} constant exists in the same descriptor;
 *       column name is taken from {@link SqlDescriptorField#columnName()}, making Read and Write
 *       share a single source of truth.</li>
 *   <li>{@code field(String, extractor)} / {@code fieldExpr(String, sql)} —
 *       fallback for fields without a matching descriptor constant; accepts camelCase Java names
 *       (e.g. {@code Fields.*} from {@code @FieldNameConstants}) and converts them to
 *       snake_case automatically ({@code updatedAt} → {@code updated_at}).</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlWriteFieldFactory {

    public static <T> SqlMappedField<T> field(SqlDescriptorField<?> descriptorField, Function<T, Object> extractor) {
        return new SqlMappedField<>(descriptorField.columnName(), extractor);
    }

    public static <T> SqlExpressionField<T> fieldExpr(SqlDescriptorField<?> descriptorField, String sqlExpression) {
        return new SqlExpressionField<>(descriptorField.columnName(), sqlExpression);
    }

    public static <T> SqlMappedField<T> field(String javaFieldName, Function<T, Object> extractor) {
        return new SqlMappedField<>(toSnakeCase(javaFieldName), extractor);
    }

    public static <T> SqlExpressionField<T> fieldExpr(String javaFieldName, String sqlExpression) {
        return new SqlExpressionField<>(toSnakeCase(javaFieldName), sqlExpression);
    }

}
