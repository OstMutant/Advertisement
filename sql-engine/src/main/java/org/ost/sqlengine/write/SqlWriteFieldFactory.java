package org.ost.sqlengine.write;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlWriteFieldFactory {

    public static <E> SqlMappedField<E> field(String column, Function<E, Object> extractor) {
        return new SqlMappedField<>(column, extractor);
    }

    public static <E> SqlMappedField<E> field(String column, String param, Function<E, Object> extractor) {
        return new SqlMappedField<>(column, param, extractor);
    }

    public static <E> SqlExpressionField<E> fieldExpr(String column, String sqlExpression) {
        return new SqlExpressionField<>(column, sqlExpression);
    }
}
