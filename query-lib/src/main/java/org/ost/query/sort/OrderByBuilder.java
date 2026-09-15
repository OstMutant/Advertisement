package org.ost.query.sort;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds a SQL ORDER BY clause from a Spring Data {@link Sort} and a {@link SortField} list carrying stable tiebreakers. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderByBuilder {

    private static final String NULLS_LAST = " NULLS LAST";

    // Priority: sort (its own order) first, then the list's own declared defaults, then tiebreakers -- each level only for properties not already claimed by a higher one.
    public static String build(Sort sort, List<SortField> fields) {
        Map<String, SortField> byProperty = fields.stream().collect(Collectors.toMap(SortField::property, f -> f));
        List<SortField> resolved = new ArrayList<>();
        Set<String> included = new HashSet<>();

        if (sort != null) {
            for (Sort.Order order : sort) {
                SortField field = byProperty.get(order.getProperty());
                if (field == null || !included.add(field.property())) continue;
                resolved.add(new SortField(field.property(), field.expression(), order.getDirection(), field.tiebreakers()));
            }
        }
        for (SortField field : fields) {
            if (field.direction() == null || !included.add(field.property())) continue;
            resolved.add(field);
        }

        List<String> clauses = new ArrayList<>();
        for (SortField field : resolved) {
            clauses.add(field.expression() + " " + field.direction().name() + NULLS_LAST);
            appendTiebreakers(field.tiebreakers(), included, clauses);
        }
        return clauses.isEmpty() ? "" : " ORDER BY " + String.join(", ", clauses);
    }

    // Skips any tiebreaker whose property is already claimed -- by sort, a list default, or an earlier field's own tiebreaker in this same call -- so it never duplicates a column.
    private static void appendTiebreakers(List<SortField> tiebreakers, Set<String> included, List<String> clauses) {
        for (SortField tiebreaker : tiebreakers) {
            if (!included.add(tiebreaker.property())) continue;
            clauses.add(tiebreaker.expression() + " " + tiebreaker.directionOrDefault().name() + NULLS_LAST);
            appendTiebreakers(tiebreaker.tiebreakers(), included, clauses);
        }
    }
}
