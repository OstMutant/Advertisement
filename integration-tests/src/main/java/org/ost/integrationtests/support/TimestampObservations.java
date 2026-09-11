package org.ost.integrationtests.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Reads a timestamp column back from the DB for clock-jitter-sensitive scenario assertions -- warns when values come back out of insertion order, fails fast when a tie or a missing value makes deriving an expected order from them ambiguous. */
@Slf4j
public final class TimestampObservations {

    private TimestampObservations() {
    }

    public static Map<Long, Instant> read(JdbcClient jdbcClient, String table, String column, List<Long> insertionOrderIds) {
        Map<Long, Instant> byId = new LinkedHashMap<>();
        jdbcClient.sql("SELECT id, " + column + " FROM " + table + " WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource("ids", insertionOrderIds.toArray(new Long[0])))
                .query((rs, rowNum) -> Map.entry(rs.getLong("id"), rs.getTimestamp(column).toInstant()))
                .list()
                .forEach(e -> byId.put(e.getKey(), e.getValue()));

        List<Instant> observed = insertionOrderIds.stream().map(byId::get).toList();
        assertThat(observed).as("every id must have a %s value", column).doesNotContainNull();
        assertThat(observed.stream().distinct().count())
                .as("%s values must be distinct -- a tie makes a derived expected order ambiguous", column)
                .isEqualTo(observed.size());
        if (!observed.equals(observed.stream().sorted().toList())) {
            log.warn("Clock non-monotonicity detected: {}.{} values out of insertion order: {}", table, column, observed);
        }
        return byId;
    }
}
