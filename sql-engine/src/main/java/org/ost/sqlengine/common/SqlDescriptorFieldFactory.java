package org.ost.sqlengine.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.sqlengine.read.SqlFieldReader;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Factory methods for creating {@link SqlDescriptorField} instances by Java type.
 * Two naming conventions are provided:
 * <ul>
 *   <li>{@code str/longVal/bool/instant/intVal} — explicit SQL expression + alias.</li>
 *   <li>{@code strCol/longCol/boolCol/instantCol/intCol} — shorthand for {@code tableAlias.column},
 *       where the column name is reused as the alias.</li>
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

    public static SqlDescriptorField<String>  strCol    (String tableAlias, String column) { return str    (tableAlias + "." + column, column); }
    public static SqlDescriptorField<Long>    longCol   (String tableAlias, String column) { return longVal(tableAlias + "." + column, column); }
    public static SqlDescriptorField<Boolean> boolCol   (String tableAlias, String column) { return bool   (tableAlias + "." + column, column); }
    public static SqlDescriptorField<Instant> instantCol(String tableAlias, String column) { return instant(tableAlias + "." + column, column); }
    public static SqlDescriptorField<Integer> intCol    (String tableAlias, String column) { return intVal (tableAlias + "." + column, column); }

    public static <T> SqlDescriptorField<T> build(String sqlExpression, String alias, SqlFieldReader<T> extractor) {
        return SqlDescriptorField.<T>builder().sqlExpression(sqlExpression).alias(alias).extractor(extractor).build();
    }
}
