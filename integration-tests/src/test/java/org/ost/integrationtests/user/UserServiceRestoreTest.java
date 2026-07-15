package org.ost.integrationtests.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.audit.config.AuditAutoConfiguration;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.CurrentActorHook;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.Role;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.entity.User;
import org.ost.user.repository.UserRepository;
import org.ost.user.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers improvement-045 item 7: {@code UserService.applyUserRestore()} is {@code private} —
 * stricter than {@code DefaultTaxonPort.resolveTranslation()}'s package-private (improvement-045
 * item 6) — so this tests it through the real public {@link UserService#restoreToSnapshot} entry
 * point instead, per {@code integration-tests/DECISIONS.md} ADR-008.
 *
 * <p>{@code restoreToSnapshot()} needs a genuinely wired {@link AuditPort} to look up the snapshot
 * it restores from, so this class boots {@link AuditAutoConfiguration} alongside
 * {@link UserAutoConfiguration}. Deliberately does <b>not</b> reuse
 * {@code org.ost.integrationtests.support.RepositoryTestSupport} here — that class declares its
 * own {@code ComponentFactory<AuditPort>} bean (named {@code auditPortFactory}, representing
 * "audit starter absent"), which collides by bean name with {@code AuditAutoConfiguration}'s own
 * real {@code auditPortFactory} bean when both are present in the same context
 * (confirmed directly: {@code BeanDefinitionOverrideException}, not a graceful
 * {@code @ConditionalOnMissingBean} skip — {@code RepositoryTestSupport} isn't itself
 * auto-configuration-ordered, so its bean registers before the conditional check on
 * {@code AuditAutoConfiguration}'s own bean can suppress it). This class supplies its own minimal
 * {@code @ImportAutoConfiguration}/{@code @EnableJdbcAuditing} instead — see
 * {@code integration-tests/CLAUDE.md} "Reusable test support" for the resulting rule: only combine
 * {@code RepositoryTestSupport} with a starter when the port(s) it stubs out are meant to be
 * absent, never alongside the real starter that provides them.</p>
 */
@SpringBootTest(classes = {
        UserAutoConfiguration.class,
        AuditAutoConfiguration.class,
        UserServiceRestoreTest.TestConfig.class
})
class UserServiceRestoreTest extends AbstractPostgresIntegrationTest {

    /**
     * Explicit allow-list, not {@code @EnableAutoConfiguration} — same rationale as {@link
     * org.ost.integrationtests.support.RepositoryTestSupport}'s own javadoc: an implicit
     * classpath-wide cascade would pull in every domain starter's {@code *AutoConfiguration} found
     * on {@code integration-tests}' classpath (e.g. {@code AdvertisementAutoConfiguration}, purely
     * because {@code advertisement-spring-boot-starter} is a dependency for an unrelated test),
     * not just the Boot JDBC/Liquibase/Transaction infrastructure this class actually needs.
     */
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

        // AuditAutoConfiguration's default auditObjectMapper has no AuditableSnapshot subtypes
        // registered — that registration only happens in marketplace-app's JacksonConfig
        // (@PostConstruct), which this module never loads. Without it, Jackson can't resolve the
        // "@type": "user" discriminator on read-back, and the snapshot deserialization silently
        // fails (logged as a WARN inside AuditLogRepository$ProjectionMapper), making
        // restoreToSnapshot() short-circuit to Optional.empty(). Mirrors JacksonConfig's own
        // registration, scoped to just the subtype this test exercises.
        @Autowired
        @Qualifier("auditObjectMapper")
        private ObjectMapper auditObjectMapper;

        @PostConstruct
        void registerAuditSnapshotSubtypes() {
            auditObjectMapper.registerSubtypes(UserSnapshotDto.class);
        }

        @Bean
        AuditorAware<Long> auditorAware() {
            return Optional::empty;
        }

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
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditPort auditPort;

    @Autowired
    private JdbcClient jdbcClient;

    private Long actorId;

    @BeforeEach
    void cleanDatabaseAndCreateActor() {
        TestDataCleaner.cleanAll(jdbcClient);
        User actor = UserTestFixtures.createTestUser(userRepository, "Actor",
                "actor-" + UUID.randomUUID() + "@example.com");
        actorId = actor.getId();
    }

    private Long latestSnapshotId(Long userId) {
        return jdbcClient.sql("""
                        SELECT id FROM audit_log
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        ORDER BY created_at DESC LIMIT 1
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("entityType", EntityType.USER.name())
                        .addValue("entityId", userId))
                .query(Long.class)
                .single();
    }

    @Test
    void restoreToSnapshot_revertsNameAndRole_andForwardsCurrentVersionNotStale() {
        User user = UserTestFixtures.createTestUser(userRepository, "Original Name",
                "user-" + UUID.randomUUID() + "@example.com");
        auditPort.captureCreation(user.getId(),
                new UserSnapshotDto("Original Name", user.getEmail(), Role.USER.name()), actorId);
        Long snapshotId = latestSnapshotId(user.getId());

        userRepository.updateProfile(new UserProfileDto(user.getId(), "Changed Name", Role.MODERATOR, user.getVersion()));
        User changed = userRepository.findById(user.getId()).orElseThrow();

        Optional<UserDto> restored = userService.restoreToSnapshot(user.getId(), snapshotId, actorId);

        assertThat(restored).isPresent();
        assertThat(restored.get().name()).isEqualTo("Original Name");
        assertThat(restored.get().role()).isEqualTo(Role.USER);

        User afterRestore = userRepository.findById(user.getId()).orElseThrow();
        assertThat(afterRestore.getName()).isEqualTo("Original Name");
        assertThat(afterRestore.getRole()).isEqualTo(Role.USER);
        // version must be forwarded from the row's CURRENT version (post-change), not re-derived
        // from a stale fetch — matches ADR-029's rule, already applied correctly by
        // applyUserRestore() forwarding before.getVersion() where "before" is re-fetched fresh.
        assertThat(afterRestore.getVersion()).isEqualTo(changed.getVersion() + 1);
    }

    @Test
    void restoreToSnapshot_unknownSnapshotId_returnsEmpty() {
        User user = UserTestFixtures.createTestUser(userRepository, "Some Name",
                "user-" + UUID.randomUUID() + "@example.com");

        Optional<UserDto> restored = userService.restoreToSnapshot(user.getId(), -1L, actorId);

        assertThat(restored).isEmpty();
    }
}
