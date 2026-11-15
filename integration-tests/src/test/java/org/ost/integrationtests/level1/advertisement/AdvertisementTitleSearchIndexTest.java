package org.ost.integrationtests.level1.advertisement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.advertisement.config.AdvertisementAutoConfiguration;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The advertisement title search clause ({@code a.title ILIKE '%text%' ESCAPE '\'}, as the title filter renders it) can be served by the
 * trigram index of the advertisement schema: with 5000 advertisements loaded and sequential scans switched off, the plan names the index;
 * a second test checks that a substring search ignores case and finds the title.
 */
@SpringBootTest(classes = {
        AdvertisementAutoConfiguration.class,
        UserAutoConfiguration.class,
        RepositoryTestSupport.class
})
class AdvertisementTitleSearchIndexTest extends AbstractPostgresIntegrationTest {

    @Autowired private JdbcClient jdbcClient;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void loadAdvertisements() {
        TestDataCleaner.cleanAll(jdbcClient);
        Long actorId = UserTestFixtures.createTestUser(userRepository, "Test Actor", "actor-" + UUID.randomUUID() + "@example.com").getId();
        jdbcClient.sql("""
                        INSERT INTO advertisement (title, ad_kind, created_by)
                        SELECT 'Item ' || md5(n::text), 'OFFER', :actor FROM generate_series(1, 5000) AS n
                        """)
                .param("actor", actorId).update();
        jdbcClient.sql("INSERT INTO advertisement (title, ad_kind, created_by) VALUES ('Vintage Bicycle Frame', 'OFFER', :actor)")
                .param("actor", actorId).update();
        jdbcClient.sql("ANALYZE advertisement").update();
    }

    @Test
    void titleSearch_usesTheTrigramIndex() {
        List<String> plan = transactionTemplate.execute(status -> {
            // Sequential scans are switched off so the plan shows whether the index can serve the clause at all, whatever the table size.
            jdbcClient.sql("SET LOCAL enable_seqscan = off").update();
            return jdbcClient.sql("EXPLAIN SELECT id FROM advertisement a WHERE a.title ILIKE '%bicycle%' ESCAPE '\\'")
                    .query(String.class).list();
        });

        assertThat(String.join("\n", plan)).contains("idx_advertisement_title_trgm");
    }

    @Test
    void titleSearch_findsTheTitleBySubstringIgnoringCase() {
        List<String> titles = jdbcClient.sql("SELECT title FROM advertisement a WHERE a.title ILIKE '%BICYCLE%' ESCAPE '\\'")
                .query(String.class).list();

        assertThat(titles).containsExactly("Vintage Bicycle Frame");
    }
}
