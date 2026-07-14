package org.ost.integrationtests.support;

import lombok.NonNull;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Truncates rows between test methods sharing one cached {@code @SpringBootTest} context — the
 * context (and its database) is reused across every test method in a class by default, so without
 * this, later tests see earlier tests' leftover rows. By analogy with Playwright's DB-reset-before
 * -run pattern.
 */
public final class TestDataCleaner {

    private TestDataCleaner() {
    }

    /**
     * Deletes all rows from each given table, in the given order — pass tables in FK-safe order
     * (dependent tables first, referenced tables last).
     */
    public static void cleanTables(@NonNull JdbcClient jdbcClient, @NonNull String... tablesInFkOrder) {
        for (String table : tablesInFkOrder) {
            jdbcClient.sql("DELETE FROM " + table).update();
        }
    }
}
