package org.ost.sqlengine.projection;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlSelectFieldFactory {

    public static SqlSelectField<String> str(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, ResultSet::getString);
    }

    /** @deprecated use {@link #longVal} */
    @Deprecated
    public static SqlSelectField<Long> id(String sqlExpression, String sqlAlias) {
        return longVal(sqlExpression, sqlAlias);
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
        return build(sqlExpression, sqlAlias, (rs, alias) -> rs.getInt(alias));
    }

    public static SqlSelectField<String[]> strArray(String sqlExpression, String sqlAlias) {
        return build(sqlExpression, sqlAlias, (rs, alias) -> {
            java.sql.Array arr = rs.getArray(alias);
            if (arr == null) return new String[0];
            return (String[]) arr.getArray();
        });
    }

    public static <T> SqlSelectField<T> build(String sqlExpression, String alias, SqlFieldReader<T> extractor) {
        return SqlSelectField.<T>builder().sqlExpression(sqlExpression).alias(alias).extractor(extractor).build();
    }
}
