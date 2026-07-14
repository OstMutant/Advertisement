package org.ost.integrationtests.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.model.Role;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.entity.User;
import org.ost.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers improvement-045 item 2: {@code UserRepository.updateProfile()} goes through the narrower
 * {@code UserProfileUpdate} entity (no {@code email}/{@code passwordHash} mapped properties) so
 * Spring Data JDBC's generated {@code UPDATE} cannot touch those fields even if a caller populates
 * the wrong DTO — see {@code user-spring-boot-starter/CLAUDE.md} and
 * {@code marketplace-app/DECISIONS.md} ADR-029. Unlike {@code Advertisement} (covered in
 * improvement-027 Batch 1), this optimistic-locking + entity-boundary behavior had zero test
 * coverage before this class.
 */
@SpringBootTest(classes = {
        UserAutoConfiguration.class,
        RepositoryTestSupport.class
})
class UserRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcClient jdbcClient;

    /** {@link TestDataCleaner#cleanAll}, not a hand-picked table subset — the singleton
     *  Testcontainers Postgres instance (see DECISIONS.md ADR-002) is shared across every test
     *  class in one {@code mvn test} run, so a row left behind by another domain's test class
     *  (e.g. {@code AdvertisementRepositoryTest}'s last test method, FK to
     *  {@code user_information}) can break a narrower cleanup this class has no reason to know
     *  about — confirmed directly by this exact failure before {@code cleanAll} existed. */
    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private User save(String name, String email, String passwordHash, Role role) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordHash)
                .role(role)
                .locale("en")
                .build());
    }

    @Test
    void updateProfile_staleVersion_throwsOptimisticLockingFailureException() {
        User saved = save("Original Name", "user-" + UUID.randomUUID() + "@example.com", "hash-1", Role.USER);

        UserProfileDto staleUpdate = new UserProfileDto(saved.getId(), "New Name", Role.MODERATOR,
                saved.getVersion() + 1);

        assertThatThrownBy(() -> userRepository.updateProfile(staleUpdate))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void updateProfile_currentVersion_succeedsAndUpdatesNameAndRole() {
        User saved = save("Original Name", "user-" + UUID.randomUUID() + "@example.com", "hash-1", Role.USER);

        UserProfileDto update = new UserProfileDto(saved.getId(), "New Name", Role.MODERATOR, saved.getVersion());
        userRepository.updateProfile(update);

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("New Name");
        assertThat(reloaded.getRole()).isEqualTo(Role.MODERATOR);
        assertThat(reloaded.getVersion()).isEqualTo(saved.getVersion() + 1);
    }

    @Test
    void updateProfile_cannotAlterEmailOrPasswordHash() {
        String originalEmail = "user-" + UUID.randomUUID() + "@example.com";
        String originalPasswordHash = "original-hash";
        User saved = save("Original Name", originalEmail, originalPasswordHash, Role.USER);

        UserProfileDto update = new UserProfileDto(saved.getId(), "New Name", Role.MODERATOR, saved.getVersion());
        userRepository.updateProfile(update);

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo(originalEmail);
        assertThat(reloaded.getPasswordHash()).isEqualTo(originalPasswordHash);
    }
}
