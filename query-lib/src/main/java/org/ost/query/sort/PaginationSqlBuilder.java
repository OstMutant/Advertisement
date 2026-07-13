package org.ost.query.sort;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PaginationSqlBuilder {

    public static String pageLimit(MapSqlParameterSource params, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) return "";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        return " LIMIT :limit OFFSET :offset";
    }
}
