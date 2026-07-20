package org.ost.integrationtests.attachment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.attachment.config.AttachmentAutoConfiguration;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.repository.AttachmentRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-027 Batch 3: {@link AttachmentRepository}'s dynamic SQL — not exhaustive,
 * the highest-risk paths: soft-delete visibility, the two-step restore-to-urls flow ({@code
 * restoreUndelete} + {@code restoreMarkDeleted}), retention-based cleanup selection, and both
 * {@code loadMediaStats} overloads (the bulk one uses a {@code ROW_NUMBER() OVER (PARTITION BY
 * entity_id ...)} window function — the most complex query in this repository).
 *
 * <p>Own minimal {@code TestConfig} rather than {@code RepositoryTestSupport} — that class
 * declares its own {@code ComponentFactory<AttachmentPort>} bean (named {@code
 * attachmentPortFactory}), which collides by bean name with {@code AttachmentAutoConfiguration}'s
 * own real {@code attachmentPortFactory} bean when both are present (confirmed directly:
 * {@code BeanDefinitionOverrideException}), same shape {@code UserServiceRestoreTest}'s javadoc
 * already documents for {@code auditPortFactory}. Same {@code @ImportAutoConfiguration} allow
 * -list as that class (see {@code integration-tests/DECISIONS.md} ADR-009).</p>
 *
 * <p>{@link AttachmentAutoConfiguration} unconditionally constructs a real {@link S3Client} (no
 * {@code @ConditionalOnMissingBean} guard tied to {@link StorageService}) from {@code storage.s3.*}
 * properties this module doesn't set — both are replaced with {@code @MockitoBean} (same
 * reasoning as {@code AttachmentServiceTransactionTest}), even though this test never calls
 * either; nothing here exercises storage, only the repository's own SQL. {@code
 * @ComponentScan}-picked-up {@code AttachmentService} also needs a {@link CurrentActorHook} bean
 * to construct at all, even though this test never calls it either.</p>
 */
@SpringBootTest(classes = {
        AttachmentAutoConfiguration.class,
        AttachmentRepositoryTest.TestConfig.class
})
class AttachmentRepositoryTest extends AbstractPostgresIntegrationTest {

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
    private AttachmentRepository attachmentRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private Attachment save(Long entityId, String url) {
        return attachmentRepository.save(Attachment.builder()
                .entityType(EntityType.ADVERTISEMENT)
                .entityId(entityId)
                .url(url)
                .filename(url)
                .contentType("image/jpeg")
                .size(100L)
                .build());
    }

    // raw insert to force a tied created_at, same technique as AuditLogRepositoryTest
    private Long insertTiedRow(Long entityId, String url, Instant createdAt) {
        return jdbcClient.sql("""
                        INSERT INTO attachment (entity_type, entity_id, url, filename, content_type, size, created_at)
                        VALUES (:entityType, :entityId, :url, :url, 'image/jpeg', 100, :createdAt)
                        RETURNING id
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", EntityType.ADVERTISEMENT.name())
                        .addValue("entityId", entityId)
                        .addValue("url", url)
                        .addValue("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)))
                .query(Long.class)
                .single();
    }

    @Test
    void save_and_getByEntityId_excludesSoftDeletedRows() {
        Attachment active = save(1L, "active.jpg");
        Attachment deleted = save(1L, "deleted.jpg");
        attachmentRepository.softDelete(deleted.getId(), 42L);

        List<Attachment> result = attachmentRepository.getByEntityId(EntityType.ADVERTISEMENT, 1L);

        assertThat(result).extracting(Attachment::getId).containsExactly(active.getId());
    }

    @Test
    void softDeleteAll_softDeletesEveryActiveAttachmentForTheEntity() {
        save(1L, "a.jpg");
        save(1L, "b.jpg");
        save(2L, "c.jpg");

        attachmentRepository.softDeleteAll(EntityType.ADVERTISEMENT, 1L, 42L);

        assertThat(attachmentRepository.getByEntityId(EntityType.ADVERTISEMENT, 1L)).isEmpty();
        assertThat(attachmentRepository.getByEntityId(EntityType.ADVERTISEMENT, 2L)).hasSize(1);
    }

    @Test
    void restoreUndeleteThenRestoreMarkDeleted_targetSetBecomesActiveRestBecomesDeleted() {
        Attachment keep = save(1L, "keep.jpg");
        save(1L, "drop.jpg");
        attachmentRepository.softDeleteAll(EntityType.ADVERTISEMENT, 1L, 42L);

        // restoring to a snapshot where only "keep.jpg" should end up active
        attachmentRepository.restoreUndelete(EntityType.ADVERTISEMENT, 1L, new String[] {"keep.jpg"});
        attachmentRepository.restoreMarkDeleted(EntityType.ADVERTISEMENT, 1L, 42L, new String[] {"keep.jpg"});

        List<Attachment> active = attachmentRepository.getByEntityId(EntityType.ADVERTISEMENT, 1L);
        assertThat(active).extracting(Attachment::getId).containsExactly(keep.getId());
    }

    private void backdateDeletedAt(Long attachmentId, int daysAgo) {
        jdbcClient.sql("UPDATE attachment SET deleted_at = :deletedAt WHERE id = :id")
                .paramSource(new MapSqlParameterSource()
                        .addValue("deletedAt", OffsetDateTime.ofInstant(
                                Instant.now().minus(daysAgo, ChronoUnit.DAYS), ZoneOffset.UTC))
                        .addValue("id", attachmentId))
                .update();
    }

    @Test
    void findUrlsDeletedOlderThan_excludesRecentlyDeletedButIncludesOldVideoRows() {
        Attachment oldDeleted = save(1L, "old.jpg");
        Attachment recentDeleted = save(1L, "recent.jpg");
        Attachment oldVideo = attachmentRepository.save(Attachment.builder()
                .entityType(EntityType.ADVERTISEMENT).entityId(1L)
                .url("https://youtube.com/watch?v=abc").filename("abc")
                .contentType("video/youtube").size(0L).build());
        attachmentRepository.softDelete(oldDeleted.getId(), 42L);
        attachmentRepository.softDelete(recentDeleted.getId(), 42L);
        attachmentRepository.softDelete(oldVideo.getId(), 42L);
        // backdate the "old" rows' deleted_at so they're actually past the retention window
        backdateDeletedAt(oldDeleted.getId(), 100);
        backdateDeletedAt(oldVideo.getId(), 100);

        List<AttachmentRepository.DeletableAttachment> candidates = attachmentRepository.findUrlsDeletedOlderThan(90);

        assertThat(candidates).extracting(AttachmentRepository.DeletableAttachment::url)
                .containsExactlyInAnyOrder("old.jpg", "https://youtube.com/watch?v=abc");
    }

    @Test
    void deleteByUrls_removesOnlyTheGivenUrls() {
        save(1L, "keep-active.jpg");
        Attachment toRemove = save(1L, "remove.jpg");
        attachmentRepository.softDelete(toRemove.getId(), 42L);

        List<String> deleted = attachmentRepository.deleteByUrls(List.of("remove.jpg"));

        assertThat(deleted).containsExactly("remove.jpg");
        assertThat(attachmentRepository.getByEntityId(EntityType.ADVERTISEMENT, 1L))
                .extracting(Attachment::getUrl).containsExactly("keep-active.jpg");
    }

    @Test
    void deleteByUrls_rowNoLongerSoftDeleted_survivesEvenIfUrlIsInTheRequestedList() {
        Attachment restored = save(1L, "restored.jpg");
        attachmentRepository.softDelete(restored.getId(), 42L);
        // simulate a concurrent restore that ran between candidate-collection and this call
        attachmentRepository.restoreUndelete(EntityType.ADVERTISEMENT, 1L, new String[] {"restored.jpg"});

        List<String> deleted = attachmentRepository.deleteByUrls(List.of("restored.jpg"));

        assertThat(deleted).isEmpty();
        assertThat(attachmentRepository.getByEntityId(EntityType.ADVERTISEMENT, 1L))
                .extracting(Attachment::getUrl).containsExactly("restored.jpg");
    }

    @Test
    void loadMediaStats_singleEntity_returnsEarliestActiveAttachmentAndCount() throws InterruptedException {
        save(1L, "first.jpg");
        Thread.sleep(10);
        save(1L, "second.jpg");

        AttachmentRepository.MediaStats stats = attachmentRepository.loadMediaStats(EntityType.ADVERTISEMENT, 1L);

        assertThat(stats.mainUrl()).isEqualTo("first.jpg");
        assertThat(stats.count()).isEqualTo(2);
    }

    @Test
    void loadMediaStats_singleEntity_noAttachments_returnsEmptyStats() {
        AttachmentRepository.MediaStats stats = attachmentRepository.loadMediaStats(EntityType.ADVERTISEMENT, 99L);

        assertThat(stats.mainUrl()).isNull();
        assertThat(stats.count()).isZero();
    }

    @Test
    void loadMediaStats_bulk_returnsPerEntityEarliestAttachmentAndCount() throws InterruptedException {
        save(1L, "ad1-first.jpg");
        Thread.sleep(10);
        save(1L, "ad1-second.jpg");
        save(2L, "ad2-only.jpg");

        Map<Long, AttachmentRepository.MediaStats> stats =
                attachmentRepository.loadMediaStats(EntityType.ADVERTISEMENT, Set.of(1L, 2L, 3L));

        assertThat(stats.get(1L).mainUrl()).isEqualTo("ad1-first.jpg");
        assertThat(stats.get(1L).count()).isEqualTo(2);
        assertThat(stats.get(2L).mainUrl()).isEqualTo("ad2-only.jpg");
        assertThat(stats.get(2L).count()).isEqualTo(1);
        assertThat(stats).doesNotContainKey(3L);
    }

    @Test
    void loadMediaStats_singleEntity_tiedCreatedAt_pickIsDeterministicByLowestId() {
        Instant tiedInstant = Instant.parse("2026-01-01T00:00:00Z");
        Long firstId = insertTiedRow(10L, "first.jpg", tiedInstant);
        insertTiedRow(10L, "second.jpg", tiedInstant);

        AttachmentRepository.MediaStats stats = attachmentRepository.loadMediaStats(EntityType.ADVERTISEMENT, 10L);

        assertThat(stats.mainUrl()).isEqualTo("first.jpg");
        assertThat(firstId).isNotNull();
    }

    @Test
    void loadMediaStats_bulkAndSingle_tiedCreatedAt_agreeOnMainAttachment() {
        Instant tiedInstant = Instant.parse("2026-01-01T00:00:00Z");
        insertTiedRow(11L, "first.jpg", tiedInstant);
        insertTiedRow(11L, "second.jpg", tiedInstant);

        AttachmentRepository.MediaStats singleStats = attachmentRepository.loadMediaStats(EntityType.ADVERTISEMENT, 11L);
        Map<Long, AttachmentRepository.MediaStats> bulkStats =
                attachmentRepository.loadMediaStats(EntityType.ADVERTISEMENT, Set.of(11L));

        assertThat(bulkStats.get(11L).mainUrl()).isEqualTo(singleStats.mainUrl());
    }
}
