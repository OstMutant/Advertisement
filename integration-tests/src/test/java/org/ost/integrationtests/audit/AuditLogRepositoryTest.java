package org.ost.integrationtests.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.audit.config.AuditAutoConfiguration;
import org.ost.audit.repository.AuditLogProjection;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.audit.spi.AuditDomainHook;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-050 item 4: {@code AuditLogRepository.findTimeline()}'s and {@code
 * getSnapshotContent()}'s {@code version}-numbering subqueries counted rows via {@code
 * b.created_at <= f.created_at} with no {@code id} tiebreaker — two rows for the same entity
 * sharing an identical {@code created_at} (plausible: two audit writes in the same transaction/
 * millisecond, e.g. a user snapshot plus its default-settings snapshot) got the <em>same</em>
 * computed version number, which {@code marketplace-app/DECISIONS.md} ADR-022's "current state"
 * badge logic relies on to distinguish timeline entries. Fixed by comparing {@code (created_at,
 * id)} tuples instead of {@code created_at} alone.
 *
 * <p>Rows are inserted directly via {@code jdbcClient}, not {@link
 * org.ost.audit.repository.AuditLogRepository#save}, specifically to force an identical {@code
 * created_at} — {@code save()} always uses the column's {@code NOW()} default, which two calls in
 * a test can never reliably tie.</p>
 *
 * <p>Own minimal {@code TestConfig} rather than {@code RepositoryTestSupport} — needs the real
 * {@link AuditAutoConfiguration} wired (not stubbed absent), same shape and same
 * {@code @ImportAutoConfiguration} allow-list as {@code UserServiceRestoreTest} (see
 * {@code integration-tests/DECISIONS.md} ADR-009) — no {@code UserAutoConfiguration} needed here,
 * {@code audit_log.actor_id} has no FK.</p>
 */
@SpringBootTest(classes = {
        AuditAutoConfiguration.class,
        AuditLogRepositoryTest.TestConfig.class
})
class AuditLogRepositoryTest extends AbstractPostgresIntegrationTest {

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
        CurrentActorHook currentActorHook() {
            return Optional::empty;
        }

        @Bean
        AuditDomainHook auditDomainHook() {
            return new AuditDomainHook() {
                @Override
                public Map<Long, String> resolveNames(Set<Long> actorIds) {
                    return Map.of();
                }

                @Override
                public Set<Long> findExisting(EntityType entityType, Set<Long> entityIds) {
                    return Set.of();
                }

                @Override
                public String resolveDisplayName(EntityType entityType, AuditableSnapshot snapshot) {
                    return "";
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> castIfKnown(
                        AuditSnapshotContentDto<? extends AuditableSnapshot> content) {
                    return Optional.of((AuditSnapshotContentDto<T>) content);
                }
            };
        }
    }

    @Autowired
    private org.ost.audit.repository.AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private Long insertRow(EntityType entityType, Long entityId, Instant createdAt) {
        return jdbcClient.sql("""
                        INSERT INTO audit_log (entity_type, entity_id, action_type, actor_id, created_at)
                        VALUES (:entityType, :entityId, 'CREATED', 1, :createdAt)
                        RETURNING id
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("entityId", entityId)
                        .addValue("createdAt", java.time.OffsetDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC),
                                java.sql.Types.TIMESTAMP_WITH_TIMEZONE))
                .query(Long.class)
                .single();
    }

    @Test
    void findTimeline_twoRowsSameCreatedAt_getDistinctVersions() {
        Instant tiedInstant = Instant.parse("2026-01-01T00:00:00Z");
        Long firstId = insertRow(EntityType.USER, 1L, tiedInstant);
        Long secondId = insertRow(EntityType.USER, 1L, tiedInstant);

        List<AuditLogProjection> rows = auditLogRepository.findTimeline(
                AuditTimelineFilterDto.empty(), Sort.by("createdAt").ascending(), 0, 10);

        Map<Long, Integer> versionById = rows.stream()
                .collect(java.util.stream.Collectors.toMap(AuditLogProjection::id, AuditLogProjection::version));
        assertThat(versionById.get(firstId)).isNotEqualTo(versionById.get(secondId));
        assertThat(versionById.get(firstId)).isEqualTo(1);
        assertThat(versionById.get(secondId)).isEqualTo(2);
    }

    @Test
    void getSnapshotContent_twoRowsSameCreatedAt_getDistinctVersions() {
        Instant tiedInstant = Instant.parse("2026-01-01T00:00:00Z");
        Long firstId = insertRow(EntityType.USER, 2L, tiedInstant);
        Long secondId = insertRow(EntityType.USER, 2L, tiedInstant);

        int firstVersion = auditLogRepository.getSnapshotContent(firstId, EntityType.USER).orElseThrow().version();
        int secondVersion = auditLogRepository.getSnapshotContent(secondId, EntityType.USER).orElseThrow().version();

        assertThat(firstVersion).isNotEqualTo(secondVersion);
        assertThat(firstVersion).isEqualTo(1);
        assertThat(secondVersion).isEqualTo(2);
    }
}
