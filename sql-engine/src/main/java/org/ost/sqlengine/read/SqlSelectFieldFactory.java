package org.ost.sqlengine.read;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Factory methods for creating {@link SqlSelectField} instances by Java type.
 * Two naming conventions are provided:
 * <ul>
 *   <li>{@code str/longVal/bool/instant/intVal} — explicit SQL expression + alias.</li>
 *   <li>{@code strCol/longCol/boolCol/instantCol/intCol} — shorthand for {@code tableAlias.column},
 *       where the column name is reused as the alias.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlSelectFieldFactory {

    public static SqlSelectField<String> str(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, ResultSet::getString);
    }

    public static SqlSelectField<Long> longVal(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, (rs, alias) -> rs.getObject(alias, Long.class));
    }

    public static SqlSelectField<Boolean> bool(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, (rs, alias) -> rs.getObject(alias, Boolean.class));
    }

    public static SqlSelectField<Instant> instant(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, (rs, alias) -> {
            Timestamp ts = rs.getTimestamp(alias);
            return ts != null ? ts.toInstant() : null;
        });
    }

    public static SqlSelectField<Integer> intVal(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, ResultSet::getInt);
    }

    public static SqlSelectField<String>  strCol    (String tableAlias, String column) { return str    (tableAlias + "." + column, column); }
    public static SqlSelectField<Long>    longCol   (String tableAlias, String column) { return longVal(tableAlias + "." + column, column); }
    public static SqlSelectField<Boolean> boolCol   (String tableAlias, String column) { return bool   (tableAlias + "." + column, column); }
    public static SqlSelectField<Instant> instantCol(String tableAlias, String column) { return instant(tableAlias + "." + column, column); }
    public static SqlSelectField<Integer> intCol    (String tableAlias, String column) { return intVal (tableAlias + "." + column, column); }

    public static <T> SqlSelectField<T> build(String sqlExpression, String alias, SqlFieldReader<T> extractor) {
        return SqlSelectField.<T>builder().sqlExpression(sqlExpression).alias(alias).extractor(extractor).build();
    }
}
