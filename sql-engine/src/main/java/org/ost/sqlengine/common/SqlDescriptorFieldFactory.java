package org.ost.sqlengine.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.sqlengine.read.SqlFieldReader;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

import static org.ost.sqlengine.common.SqlNamingUtil.toSnakeCase;

/**
 * Factory methods for creating {@link SqlDescriptorField} instances by Java type.
 * Three naming conventions are provided:
 * <ul>
 *   <li>{@code str/longVal/bool/instant/intVal} — explicit SQL expression + alias.</li>
 *   <li>{@code strCol/longCol/boolCol/instantCol/intCol(tableAlias, column)} — shorthand for
 *       {@code tableAlias.snake_case(column)}, where the converted column name is reused as alias.
 *       Accepts both snake_case and camelCase column names.</li>
 *   <li>{@code strCol/longCol/boolCol/instantCol/intCol(column)} — single-arg form for tables
 *       queried without an alias; uses {@code snake_case(column)} as both expression and alias.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlDescriptorFieldFactory {

    public static SqlDescriptorField<String> str(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, ResultSet::getString);
    }

    public static SqlDescriptorField<Long> longVal(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, (rs, alias) -> rs.getObject(alias, Long.class));
    }

    public static SqlDescriptorField<Boolean> bool(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, (rs, alias) -> rs.getObject(alias, Boolean.class));
    }

    public static SqlDescriptorField<Instant> instant(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, (rs, alias) -> {
            Timestamp ts = rs.getTimestamp(alias);
            return ts != null ? ts.toInstant() : null;
        });
    }

    public static SqlDescriptorField<Integer> intVal(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, ResultSet::getInt);
    }

    public static SqlDescriptorField<String>  strCol    (String tableAlias, String column) { String col = toSnakeCase(column); return str    (tableAlias + "." + col, col); }
    public static SqlDescriptorField<Long>    longCol   (String tableAlias, String column) { String col = toSnakeCase(column); return longVal(tableAlias + "." + col, col); }
    public static SqlDescriptorField<Boolean> boolCol   (String tableAlias, String column) { String col = toSnakeCase(column); return bool   (tableAlias + "." + col, col); }
    public static SqlDescriptorField<Instant> instantCol(String tableAlias, String column) { String col = toSnakeCase(column); return instant(tableAlias + "." + col, col); }
    public static SqlDescriptorField<Integer> intCol    (String tableAlias, String column) { String col = toSnakeCase(column); return intVal (tableAlias + "." + col, col); }

    public static SqlDescriptorField<String>  strCol    (String column) { String col = toSnakeCase(column); return str    (col, col); }
    public static SqlDescriptorField<Long>    longCol   (String column) { String col = toSnakeCase(column); return longVal(col, col); }
    public static SqlDescriptorField<Boolean> boolCol   (String column) { String col = toSnakeCase(column); return bool   (col, col); }
    public static SqlDescriptorField<Instant> instantCol(String column) { String col = toSnakeCase(column); return instant(col, col); }
    public static SqlDescriptorField<Integer> intCol    (String column) { String col = toSnakeCase(column); return intVal (col, col); }

    public static <T> SqlDescriptorField<T> build(String sqlExpression, String alias, SqlFieldReader<T> extractor) {
        return SqlDescriptorField.<T>builder().sqlExpression(sqlExpression).alias(alias).extractor(extractor).build();
    }
}
