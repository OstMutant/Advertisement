package org.ost.integrationtests.apikey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.apikey.config.ApiKeyAutoConfiguration;
import org.ost.apikey.entity.ApiKey;
import org.ost.apikey.repository.ApiKeyRepository;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.entity.User;
import org.ost.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        UserAutoConfiguration.class,
        ApiKeyAutoConfiguration.class,
        RepositoryTestSupport.class
})
class ApiKeyRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private Long userId;

    @BeforeEach
    void cleanDatabaseAndCreateUser() {
        TestDataCleaner.cleanAll(jdbcClient);
        User user = UserTestFixtures.createTestUser(userRepository, "Api Key Test User", "apikey-" + UUID.randomUUID() + "@example.com");
        userId = user.getId();
    }

    private ApiKey saveKey(String keyHash) {
        return apiKeyRepository.save(ApiKey.builder()
                .actorId(userId)
                .keyHash(keyHash)
                .keyPrefix(keyHash.substring(0, 10))
                .label("test key")
                .build());
    }

    @Test
    void findActiveByKeyHash_activeKey_returnsIt() {
        ApiKey saved = saveKey("hash-" + UUID.randomUUID());

        Optional<ApiKey> found = apiKeyRepository.findActiveByKeyHash(saved.getKeyHash());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findActiveByKeyHash_revokedKey_returnsEmpty() {
        ApiKey saved = saveKey("hash-" + UUID.randomUUID());
        apiKeyRepository.revoke(userId, saved.getId());

        assertThat(apiKeyRepository.findActiveByKeyHash(saved.getKeyHash())).isEmpty();
    }

    @Test
    void findActiveByKeyHash_unknownHash_returnsEmpty() {
        assertThat(apiKeyRepository.findActiveByKeyHash("no-such-hash")).isEmpty();
    }

    @Test
    void findByActorId_returnsOnlyThatUsersKeys() {
        saveKey("hash-" + UUID.randomUUID());
        saveKey("hash-" + UUID.randomUUID());
        User otherUser = UserTestFixtures.createTestUser(userRepository, "Other User", "other-" + UUID.randomUUID() + "@example.com");
        apiKeyRepository.save(ApiKey.builder()
                .actorId(otherUser.getId())
                .keyHash("hash-" + UUID.randomUUID())
                .keyPrefix("otherprefix")
                .build());

        List<ApiKey> keys = apiKeyRepository.findByActorId(userId);

        assertThat(keys).hasSize(2);
        assertThat(keys).allMatch(k -> k.getActorId().equals(userId));
    }

    @Test
    void revoke_ownedKey_setsRevokedAt() {
        ApiKey saved = saveKey("hash-" + UUID.randomUUID());

        apiKeyRepository.revoke(userId, saved.getId());

        ApiKey reloaded = apiKeyRepository.findByActorId(userId).getFirst();
        assertThat(reloaded.getRevokedAt()).isNotNull();
    }

    @Test
    void revoke_calledTwice_isIdempotent() {
        ApiKey saved = saveKey("hash-" + UUID.randomUUID());

        apiKeyRepository.revoke(userId, saved.getId());
        apiKeyRepository.revoke(userId, saved.getId());

        assertThat(apiKeyRepository.findByActorId(userId).getFirst().getRevokedAt()).isNotNull();
    }

    @Test
    void revoke_wrongOwner_doesNotRevoke() {
        ApiKey saved = saveKey("hash-" + UUID.randomUUID());
        User otherUser = UserTestFixtures.createTestUser(userRepository, "Not The Owner", "notowner-" + UUID.randomUUID() + "@example.com");

        apiKeyRepository.revoke(otherUser.getId(), saved.getId());

        assertThat(apiKeyRepository.findActiveByKeyHash(saved.getKeyHash())).isPresent();
    }

    @Test
    void touchLastUsed_setsTimestamp() {
        ApiKey saved = saveKey("hash-" + UUID.randomUUID());
        assertThat(saved.getLastUsedAt()).isNull();

        apiKeyRepository.touchLastUsed(saved.getId());

        assertThat(apiKeyRepository.findByActorId(userId).getFirst().getLastUsedAt()).isNotNull();
    }

    @Test
    void deleteAllForActor_removesOnlyThatActorsKeys() {
        saveKey("hash-" + UUID.randomUUID());
        saveKey("hash-" + UUID.randomUUID());
        User otherUser = UserTestFixtures.createTestUser(userRepository, "Other User", "other-" + UUID.randomUUID() + "@example.com");
        apiKeyRepository.save(ApiKey.builder()
                .actorId(otherUser.getId())
                .keyHash("hash-" + UUID.randomUUID())
                .keyPrefix("otherprefix")
                .build());

        apiKeyRepository.deleteAllForActor(userId);

        assertThat(apiKeyRepository.findByActorId(userId)).isEmpty();
        assertThat(apiKeyRepository.findByActorId(otherUser.getId())).hasSize(1);
    }
}
