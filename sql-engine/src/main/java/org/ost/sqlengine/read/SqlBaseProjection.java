package org.ost.sqlengine.read;

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

abstract class SqlBaseProjection<T> implements RowMapper<T> {

    private final Map<String, String> aliasToSqlMap;

    protected SqlBaseProjection(List<SqlSelectField<?>> items) {
        Objects.requireNonNull(items, "Parameter 'items' must not be null.");
        this.aliasToSqlMap = items.stream()
                .collect(Collectors.toMap(
                        SqlSelectField::alias,
                        SqlSelectField::sqlExpression,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
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
