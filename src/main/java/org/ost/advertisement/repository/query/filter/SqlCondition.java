package org.ost.advertisement.repository.query.filter;

import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public record SqlCondition<R>(
        String sqlExpression,
        String filterProperty,
        R value,
        SqlOperator operator
) {

    public static <R> SqlCondition<R> of(String sqlExpression, String filterProperty, R value, SqlOperator operator) {
        return new SqlCondition<>(sqlExpression, filterProperty, value, operator);
    }

    public String getConditionClause() {
        return operator.formatClause(sqlExpression, filterProperty);
    }

    public static SqlCondition<String> like(FilterMapping filterMapping, String value) {
        return applyIfPresent(filterMapping, value, SqlOperator.LIKE_IGNORE_CASE, v -> "%" + v + "%");
    }

    public static SqlCondition<String> equalsTo(FilterMapping filterMapping, String value) {
        return applyIfPresent(filterMapping, value, SqlOperator.EQUALS, Function.identity());
    }

    public static SqlCondition<Timestamp> after(FilterMapping filterMapping, Instant value) {
        return applyIfPresent(filterMapping, value, SqlOperator.GREATER_OR_EQUAL, Timestamp::from);
    }

    public static SqlCondition<Timestamp> before(FilterMapping filterMapping, Instant value) {
        return applyIfPresent(filterMapping, value, SqlOperator.LESS_OR_EQUAL, Timestamp::from);
    }

    public static SqlCondition<Long> after(FilterMapping filterMapping, Long value) {
        return applyIfPresent(filterMapping, value, SqlOperator.GREATER_OR_EQUAL, Function.identity());
    }

    public static SqlCondition<Long> before(FilterMapping filterMapping, Long value) {
        return applyIfPresent(filterMapping, value, SqlOperator.LESS_OR_EQUAL, Function.identity());
    }

    public static <E extends Enum<E>> SqlCondition<Collection<String>> inSet(
            FilterMapping filterMapping, Set<E> values) {
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        return applyIfPresent(filterMapping, values, SqlOperator.IN, v -> v.stream().map(Enum::name).toList());
    }

    private static <I1, R1> SqlCondition<R1> applyIfPresent(FilterMapping filterMapping, I1 value,
                                                            SqlOperator operator,
                                                            Function<I1, R1> valueMapper) {
        return value != null
                ? SqlCondition.of(filterMapping.sqlExpression(), filterMapping.filterProperty(),
                valueMapper.apply(value), operator)
                : null;
    }

    public enum SqlOperator {
        EQUALS {
            @Override
            public String formatClause(String sqlExpression, String filterProperty) {
                return sqlExpression + " = :" + filterProperty;
            }
        },
        LESS_OR_EQUAL {
            @Override
            public String formatClause(String sqlExpression, String filterProperty) {
                return sqlExpression + " <= :" + filterProperty;
            }
        },
        GREATER_OR_EQUAL {
            @Override
            public String formatClause(String sqlExpression, String filterProperty) {
                return sqlExpression + " >= :" + filterProperty;
            }
        },
        LIKE_IGNORE_CASE {
            @Override
            public String formatClause(String sqlExpression, String filterProperty) {
                return sqlExpression + " ILIKE :" + filterProperty;
            }
        },
        IN {
            @Override
            public String formatClause(String sqlExpression, String filterProperty) {
                return sqlExpression + " IN (:" + filterProperty + ")";
            }
        };

        public abstract String formatClause(String sqlExpression, String filterProperty);
    }
}
