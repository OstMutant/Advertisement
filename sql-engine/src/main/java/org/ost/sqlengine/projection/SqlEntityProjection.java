package org.ost.sqlengine.projection;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public abstract class SqlEntityProjection<T> implements RowMapper<T> {

    private final Map<String, String> aliasToSqlMap;
    @Getter
    private final String sqlSource;
    @Getter
    private final String countSource;

    protected SqlEntityProjection(List<SqlSelectField<?>> items, String sqlSource) {
        this(items, sqlSource, sqlSource);
    }

    protected SqlEntityProjection(List<SqlSelectField<?>> items, String sqlSource, String countSource) {
        Objects.requireNonNull(items, "Parameter 'items' must not be null.");
        Objects.requireNonNull(sqlSource, "Parameter 'sqlSource' must not be null.");
        Objects.requireNonNull(countSource, "Parameter 'countSource' must not be null.");
        this.aliasToSqlMap = items.stream()
                .collect(Collectors.toMap(
                        SqlSelectField::alias,
                        SqlSelectField::sqlExpression,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
        this.sqlSource = sqlSource;
        this.countSource = countSource;
    }

    public String getSelectClause() {
        return aliasToSqlMap.entrySet().stream()
                .map(e -> e.getValue() + " AS " + e.getKey())
                .collect(Collectors.joining(", "));
    }

    public String getOrderByClause(Sort sort) {
        String orderByFragment = ofNullable(sort)
                .filter(s -> !s.isEmpty())
                .map(Sort::stream)
                .orElseGet(Stream::empty)
                .map(order -> ofNullable(aliasToSqlMap.get(order.getProperty()))
                        .map(col -> col + " " + order.getDirection().name() + " NULLS LAST")
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return StringUtils.isBlank(orderByFragment) ? "" : "ORDER BY " + orderByFragment;
    }
}
