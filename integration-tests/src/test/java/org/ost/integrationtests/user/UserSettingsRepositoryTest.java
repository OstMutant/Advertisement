package org.ost.integrationtests.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.model.Role;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.entity.User;
import org.ost.user.repository.UserRepository;
import org.ost.user.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers improvement-066: {@code UserSettingsRepository.save()} previously had no optimistic-
 * locking version check at all, unlike every other mutable entity in this codebase (ADR-029) --
 * two browser tabs saving settings for the same user would silently clobber each other with no
 * conflict signal. The version now lives inside the {@code settings} JSONB column itself
 * (extracted via {@code settings->>'version'} in the UPDATE's WHERE clause) rather than a separate
 * SQL column, since {@code UserSettingsRepository} already serializes the whole
 * {@link UserSettingsDto} directly into that column.
 */
@SpringBootTest(classes = {
        UserAutoConfiguration.class,
        RepositoryTestSupport.class
})
class UserSettingsRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserSettingsRepository settingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private Long createTestUser() {
        User saved = userRepository.save(User.builder()
                .name("Settings Test User")
                .email("settings-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .locale("en")
                .build());
        return saved.getId();
    }

    @Test
    void save_freshUser_startsAtVersionZeroAndSucceeds() {
        Long userId = createTestUser();

        UserSettingsDto loaded = settingsRepository.load(userId);
        assertThat(loaded.getVersion()).isEqualTo(0);

        settingsRepository.save(userId, loaded.toBuilder().adsPageSize(30).build());

        UserSettingsDto reloaded = settingsRepository.load(userId);
        assertThat(reloaded.getAdsPageSize()).isEqualTo(30);
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    @Test
    void save_staleVersion_throwsOptimisticLockingFailureException() {
        Long userId = createTestUser();
        UserSettingsDto initial = settingsRepository.load(userId);

        // First save succeeds, bumping the stored version to 1.
        settingsRepository.save(userId, initial.toBuilder().adsPageSize(30).build());

        // Second save still carries the stale version 0 -- simulates a second browser tab that
        // read settings before the first tab's save landed.
        assertThatThrownBy(() -> settingsRepository.save(userId, initial.toBuilder().timelinePageSize(40).build()))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // The first tab's change must survive untouched.
        UserSettingsDto reloaded = settingsRepository.load(userId);
        assertThat(reloaded.getAdsPageSize()).isEqualTo(30);
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    @Test
    void save_currentVersion_succeedsAndIncrementsVersion() {
        Long userId = createTestUser();
        UserSettingsDto initial = settingsRepository.load(userId);
        settingsRepository.save(userId, initial.toBuilder().adsPageSize(30).build());

        UserSettingsDto current = settingsRepository.load(userId);
        settingsRepository.save(userId, current.toBuilder().timelinePageSize(40).build());

        UserSettingsDto reloaded = settingsRepository.load(userId);
        assertThat(reloaded.getAdsPageSize()).isEqualTo(30);
        assertThat(reloaded.getTimelinePageSize()).isEqualTo(40);
        assertThat(reloaded.getVersion()).isEqualTo(2);
    }
}
