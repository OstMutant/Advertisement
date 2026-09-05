package org.ost.integrationtests.level1.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.model.Role;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.entity.User;
import org.ost.user.repository.UserPreferencesRepository;
import org.ost.user.repository.UserRepository;
import org.ost.user.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Settings optimistic locking lives inside the JSONB column itself, not a separate SQL column.
@SpringBootTest(classes = {
        UserAutoConfiguration.class,
        RepositoryTestSupport.class
})
class UserPreferencesRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private Long createTestUserWithPreferences() {
        User saved = userRepository.save(User.builder()
                .name("Preferences Test User")
                .email("preferences-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .build());
        preferencesRepository.insertDefault(saved.getId());
        return saved.getId();
    }

    @Test
    void register_realFlow_createsPreferencesRowWithDefaults() {
        String email = "registered-" + UUID.randomUUID() + "@example.com";
        SignUpDto signUp = new SignUpDto();
        signUp.setName("Registered User");
        signUp.setEmail(email);
        signUp.setPassword("password123");

        userService.register(signUp, "203.0.113.5");

        Long actorId = userRepository.findByEmail(email).orElseThrow().getId();
        assertThat(preferencesRepository.findLocaleByActorId(actorId)).isNull();
        assertThat(preferencesRepository.loadSettings(actorId).getAdsPageSize()).isEqualTo(20);
    }

    @Test
    void loadSettings_legacyRowWithNoSchemaVersionKey_stillLoadsCorrectly() {
        Long actorId = userRepository.save(User.builder()
                .name("Legacy Row User")
                .email("legacy-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .build()).getId();
        jdbcClient.sql("""
                        INSERT INTO user_preferences (actor_id, settings)
                        VALUES (:actorId, '{"adsPageSize":20,"usersPageSize":20,"timelinePageSize":20,"version":0}'::jsonb)
                        """)
                .paramSource(new MapSqlParameterSource("actorId", actorId))
                .update();

        UserSettingsDto loaded = preferencesRepository.loadSettings(actorId);

        assertThat(loaded.getTimelinePageSize()).isEqualTo(20);
        assertThat(loaded.getSchemaVersion()).isEqualTo(UserSettingsDto.SCHEMA_VERSION);
    }

    @Test
    void insertDefault_freshActor_localeIsNullAndSettingsAreDefaults() {
        Long actorId = createTestUserWithPreferences();

        assertThat(preferencesRepository.findLocaleByActorId(actorId)).isNull();
        UserSettingsDto loaded = preferencesRepository.loadSettings(actorId);
        assertThat(loaded.getAdsPageSize()).isEqualTo(20);
        assertThat(loaded.getUsersPageSize()).isEqualTo(20);
        assertThat(loaded.getTimelinePageSize()).isEqualTo(20);
        assertThat(loaded.getSchemaVersion()).isEqualTo(UserSettingsDto.SCHEMA_VERSION);
    }

    @Test
    void updateLocale_persistsAndIsReadable() {
        Long actorId = createTestUserWithPreferences();

        preferencesRepository.updateLocale(actorId, "uk");

        assertThat(preferencesRepository.findLocaleByActorId(actorId)).isEqualTo("uk");
    }

    @Test
    void saveSettings_freshActor_startsAtVersionZeroAndSucceeds() {
        Long actorId = createTestUserWithPreferences();

        UserSettingsDto loaded = preferencesRepository.loadSettings(actorId);
        assertThat(loaded.getVersion()).isEqualTo(0);

        preferencesRepository.saveSettings(actorId, loaded.toBuilder().adsPageSize(30).build());

        UserSettingsDto reloaded = preferencesRepository.loadSettings(actorId);
        assertThat(reloaded.getAdsPageSize()).isEqualTo(30);
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    @Test
    void saveSettings_staleVersion_throwsOptimisticLockingFailureException() {
        Long actorId = createTestUserWithPreferences();
        UserSettingsDto initial = preferencesRepository.loadSettings(actorId);

        preferencesRepository.saveSettings(actorId, initial.toBuilder().adsPageSize(30).build());

        // Stale version 0 simulates a second browser tab that read before the first tab's save landed.
        assertThatThrownBy(() -> preferencesRepository.saveSettings(actorId, initial.toBuilder().timelinePageSize(40).build()))
                .isInstanceOf(OptimisticLockingFailureException.class);

        UserSettingsDto reloaded = preferencesRepository.loadSettings(actorId);
        assertThat(reloaded.getAdsPageSize()).isEqualTo(30);
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    @Test
    void saveSettings_currentVersion_succeedsAndIncrementsVersion() {
        Long actorId = createTestUserWithPreferences();
        UserSettingsDto initial = preferencesRepository.loadSettings(actorId);
        preferencesRepository.saveSettings(actorId, initial.toBuilder().adsPageSize(30).build());

        UserSettingsDto current = preferencesRepository.loadSettings(actorId);
        preferencesRepository.saveSettings(actorId, current.toBuilder().timelinePageSize(40).build());

        UserSettingsDto reloaded = preferencesRepository.loadSettings(actorId);
        assertThat(reloaded.getAdsPageSize()).isEqualTo(30);
        assertThat(reloaded.getTimelinePageSize()).isEqualTo(40);
        assertThat(reloaded.getVersion()).isEqualTo(2);
    }
}
