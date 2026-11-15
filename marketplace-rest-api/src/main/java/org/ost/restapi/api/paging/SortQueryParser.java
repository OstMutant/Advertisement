package org.ost.restapi.api.paging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Parses a {@code ?sort=field,dir} query parameter into a {@link Sort}, validated against an allow-list; the defaulting overload also makes the order total. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SortQueryParser {

    private static final String ID = "id";

    public static Sort parse(String sortParam, Set<String> allowedFields) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.unsorted();
        }
        String[] parts = sortParam.split(",", 2);
        String field = parts[0].trim();
        if (!allowedFields.contains(field)) {
            throw new IllegalArgumentException("Unknown sort field: " + field);
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }

    /** Parses like {@link #parse(String, Set)}, uses {@code defaultSort} when none is given, and always ends the order with {@code id} so pages never repeat or skip rows. */
    public static Sort parse(String sortParam, Set<String> allowedFields, Sort defaultSort) {
        Sort parsed = parse(sortParam, allowedFields);
        Sort base = parsed.isSorted() ? parsed : defaultSort;
        return base.getOrderFor(ID) == null ? base.and(Sort.by(Sort.Direction.DESC, ID)) : base;
    }
}
