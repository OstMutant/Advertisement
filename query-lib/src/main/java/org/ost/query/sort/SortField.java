package org.ost.query.sort;

import org.springframework.data.domain.Sort;

import java.util.List;

/** One sortable field for {@link OrderByBuilder#build(Sort, List)}: property, SQL expression, direction (read only when acting as a tiebreaker or empty-sort default), and its own tiebreakers. */
public record SortField(String property, String expression, Sort.Direction direction, List<SortField> tiebreakers) {

    private static final Sort.Direction DEFAULT_TIEBREAKER_DIRECTION = Sort.Direction.DESC;

    public static SortField of(String property, String expression) {
        return new SortField(property, expression, null, List.of());
    }

    public static SortField of(String property, String expression, SortField... tiebreakers) {
        return new SortField(property, expression, null, List.of(tiebreakers));
    }

    public static SortField of(String property, String expression, Sort.Direction direction, SortField... tiebreakers) {
        return new SortField(property, expression, direction, List.of(tiebreakers));
    }

    Sort.Direction directionOrDefault() {
        return direction != null ? direction : DEFAULT_TIEBREAKER_DIRECTION;
    }
}
