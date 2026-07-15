package org.ost.integrationtests.support;

import lombok.NonNull;
import org.springframework.jdbc.BadSqlGrammarException;
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

    private static final String UNDEFINED_TABLE_SQLSTATE = "42P01";

    private TestDataCleaner() {
    }

    /**
     * Deletes all rows from each given table, in the given order — pass tables in FK-safe order
     * (dependent tables first, referenced tables last). Prefer {@link #cleanAll} in
     * {@code *RepositoryTest} classes unless there is a specific reason to clean only a subset.
     *
     * <p>Silently skips a table that doesn't exist yet (Postgres SQLSTATE {@code 42P01}, undefined
     * table) rather than failing. Which domain schemas exist at any point depends on which
     * {@code *RepositoryTest} classes have already run in this reactor's shared singleton
     * container — each domain's Liquibase changelog is applied by whichever test class's own
     * {@code @SpringBootTest(classes = {...})} actually imports that domain's
     * {@code *AutoConfiguration}, not by {@link RepositoryTestSupport} (which deliberately imports
     * only Spring Boot JDBC/Liquibase/Transaction infrastructure, never any domain starter's
     * autoconfiguration — see its own javadoc). A class with a narrower Spring context legitimately
     * running before the class that owns a given table is expected, not an error: there is simply
     * nothing to clean yet for a domain whose schema hasn't been created.</p>
     */
    public static void cleanTables(@NonNull JdbcClient jdbcClient, @NonNull String... tablesInFkOrder) {
        for (String table : tablesInFkOrder) {
            try {
                jdbcClient.sql("DELETE FROM " + table).update();
            } catch (BadSqlGrammarException e) {
                if (!isUndefinedTable(e)) {
                    throw e;
                }
            }
        }
    }

    private static boolean isUndefinedTable(BadSqlGrammarException e) {
        return e.getSQLException() != null && UNDEFINED_TABLE_SQLSTATE.equals(e.getSQLException().getSQLState());
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
                "attachment_snapshot", "attachment",
                "advertisement",
                "user_information");
    }
}
