package org.ost.restapi.api.paging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Parses a {@code ?sort=field,dir} query parameter into a {@link Sort}, validated against an allow-list. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SortQueryParser {

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
}
