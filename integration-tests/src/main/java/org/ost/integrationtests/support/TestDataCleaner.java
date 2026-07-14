package org.ost.integrationtests.support;

import lombok.NonNull;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Truncates rows between test methods sharing one cached {@code @SpringBootTest} context — the
 * context (and its database) is reused across every test method in a class by default, so without
 * this, later tests see earlier tests' leftover rows. By analogy with Playwright's DB-reset-before
 * -run pattern.
 *
 * <p>All {@code *RepositoryTest} classes share one physical Testcontainers Postgres instance for
 * the whole {@code mvn test} reactor run (the singleton-container pattern — see DECISIONS.md
 * ADR-002), not just within their own class. A class that only cleans its own domain's tables can
 * still fail on a foreign-key violation left behind by a <em>different</em> test class that ran
 * earlier in the same reactor run (confirmed directly: {@code AdvertisementRepositoryTest}'s last
 * test method leaves one {@code advertisement} row referencing {@code user_information}, which
 * broke a plain {@code DELETE FROM user_information} in a later-running {@code
 * UserRepositoryTest} that had no reason to know about the {@code advertisement} table at all).
 * {@link #cleanAll} exists specifically so no test class has to individually track which other
 * domains might hold a foreign key into its own tables — every {@code *RepositoryTest} should call
 * it, not hand-pick a table subset.</p>
 */
public final class TestDataCleaner {

    private TestDataCleaner() {
    }

    /**
     * Deletes all rows from each given table, in the given order — pass tables in FK-safe order
     * (dependent tables first, referenced tables last). Prefer {@link #cleanAll} in
     * {@code *RepositoryTest} classes unless there is a specific reason to clean only a subset.
     */
    public static void cleanTables(@NonNull JdbcClient jdbcClient, @NonNull String... tablesInFkOrder) {
        for (String table : tablesInFkOrder) {
            jdbcClient.sql("DELETE FROM " + table).update();
        }
    }

    /**
     * Deletes every row from every table currently known to this module, across every domain, in
     * one FK-safe order — the default choice for any {@code *RepositoryTest}'s {@code @BeforeEach}
     * cleanup, since the singleton container means any class can be running after any other. Add
     * a new table here (in FK-safe position) whenever a new domain's {@code *RepositoryTest} is
     * added — never let a new test class default to cleaning only its own tables.
     */
    public static void cleanAll(@NonNull JdbcClient jdbcClient) {
        cleanTables(jdbcClient,
                "taxon_assignment", "taxon_translation", "taxon",
                "advertisement",
                "user_information");
    }
}
