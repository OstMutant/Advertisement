package org.ost.integrationtests.attachment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.attachment.config.AttachmentAutoConfiguration;
import org.ost.attachment.repository.AttachmentMediaChange;
import org.ost.attachment.repository.AttachmentSnapshotRepository;
import org.ost.attachment.services.StorageService;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.CurrentActorHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Covers improvement-070: extractUrls() switched from an unsafe (String[]) cast + silent
// catch-all to Array.getResultSet() (no cast at all) + a logged SQLException. Round-tripping
// multiple urls through a real Postgres text[] column exercises the new extraction for real,
// rather than mocking java.sql.Array/ResultSet.
@SpringBootTest(classes = {
        AttachmentAutoConfiguration.class,
        AttachmentSnapshotRepositoryTest.TestConfig.class
})
class AttachmentSnapshotRepositoryTest extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcClientAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            DataJdbcRepositoriesAutoConfiguration.class,
            LiquibaseAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            ConfigurationPropertiesAutoConfiguration.class
    })
    @EnableJdbcAuditing
    static class TestConfig {

        @Bean
        AuditorAware<Long> auditorAware() {
            return Optional::empty;
        }

        @Bean
        CurrentActorHook currentActorHook() {
            return Optional::empty;
        }
    }

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private StorageService storageService;

    @Autowired
    private AttachmentSnapshotRepository snapshotRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    @Test
    void insert_and_getPrevUrls_roundTripsMultipleUrlsInOrder() {
        String[] urls = {"https://s3.example/a.jpg", "https://s3.example/b.jpg", "https://s3.example/c.jpg"};
        snapshotRepository.insert(EntityType.ADVERTISEMENT, 1L, urls,
                List.of(new AttachmentMediaChange(null, List.of("a.jpg", "b.jpg", "c.jpg"))), 42L);

        Optional<List<String>> result = snapshotRepository.getPrevUrls(EntityType.ADVERTISEMENT, 1L);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(urls);
    }

    @Test
    void insert_and_getUrlsById_roundTripsUrls() {
        String[] urls = {"https://s3.example/single.jpg"};
        snapshotRepository.insert(EntityType.ADVERTISEMENT, 2L, urls,
                List.of(new AttachmentMediaChange(null, List.of("single.jpg"))), 42L);
        Long id = snapshotRepository.findLatestId(EntityType.ADVERTISEMENT, 2L).orElseThrow();

        Optional<List<String>> result = snapshotRepository.getUrlsById(id);

        assertThat(result).contains(List.of("https://s3.example/single.jpg"));
    }

    @Test
    void getPrevUrls_noSnapshotExists_returnsEmpty() {
        Optional<List<String>> result = snapshotRepository.getPrevUrls(EntityType.ADVERTISEMENT, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    void deleteOlderThan_removesOnlyRowsOlderThanTheGivenDays() {
        String[] oldUrls = {"https://s3.example/old.jpg"};
        snapshotRepository.insert(EntityType.ADVERTISEMENT, 3L, oldUrls,
                List.of(new AttachmentMediaChange(null, List.of("old.jpg"))), 42L);
        Long oldId = snapshotRepository.findLatestId(EntityType.ADVERTISEMENT, 3L).orElseThrow();
        jdbcClient.sql("UPDATE attachment_snapshot SET created_at = :createdAt WHERE id = :id")
                .paramSource(new MapSqlParameterSource()
                        .addValue("createdAt", OffsetDateTime.ofInstant(
                                Instant.now().minus(100, ChronoUnit.DAYS), ZoneOffset.UTC))
                        .addValue("id", oldId))
                .update();

        String[] recentUrls = {"https://s3.example/recent.jpg"};
        snapshotRepository.insert(EntityType.ADVERTISEMENT, 4L, recentUrls,
                List.of(new AttachmentMediaChange(null, List.of("recent.jpg"))), 42L);
        Long recentId = snapshotRepository.findLatestId(EntityType.ADVERTISEMENT, 4L).orElseThrow();

        int deleted = snapshotRepository.deleteOlderThan(90);

        assertThat(deleted).isEqualTo(1);
        assertThat(snapshotRepository.getUrlsById(oldId)).isEmpty();
        assertThat(snapshotRepository.getUrlsById(recentId)).isPresent();
    }
}
