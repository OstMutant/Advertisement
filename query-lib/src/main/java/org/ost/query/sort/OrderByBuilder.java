package org.ost.query.sort;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderByBuilder {

    public static String build(Sort sort, Map<String, String> aliasToExpression) {
        if (sort == null || sort.isEmpty()) return "";
        String clause = sort.stream()
                .map(order -> {
                    String expr = aliasToExpression.get(order.getProperty());
                    return expr == null ? null : expr + " " + order.getDirection().name() + " NULLS LAST";
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return clause.isBlank() ? "" : " ORDER BY " + clause;
    }
}
