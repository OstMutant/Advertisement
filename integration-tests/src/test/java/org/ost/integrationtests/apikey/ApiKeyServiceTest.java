package org.ost.integrationtests.apikey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.apikey.config.ApiKeyAutoConfiguration;
import org.ost.apikey.services.ApiKeyService;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
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
class ApiKeyServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private Long userId;

    @BeforeEach
    void cleanDatabaseAndCreateUser() {
        TestDataCleaner.cleanAll(jdbcClient);
        User user = UserTestFixtures.createTestUser(userRepository, "Api Key Service Test User", "apikeysvc-" + UUID.randomUUID() + "@example.com");
        userId = user.getId();
    }

    @Test
    void create_thenResolveActorId_returnsTheSameUser() {
        String rawKey = apiKeyService.create(userId, "my key");

        Optional<Long> resolved = apiKeyService.resolveActorId(rawKey);

        assertThat(resolved).contains(userId);
    }

    @Test
    void resolveActorId_unknownKey_returnsEmpty() {
        assertThat(apiKeyService.resolveActorId("no-such-raw-key")).isEmpty();
    }

    @Test
    void resolveActorId_revokedKey_returnsEmpty() {
        String rawKey = apiKeyService.create(userId, "my key");
        Long keyId = apiKeyService.listForActor(userId).getFirst().id();

        apiKeyService.revoke(userId, keyId);

        assertThat(apiKeyService.resolveActorId(rawKey)).isEmpty();
    }

    @Test
    void listForActor_neverExposesHashOrRawKey() {
        apiKeyService.create(userId, "my key");

        List<ApiKeySummaryDto> keys = apiKeyService.listForActor(userId);

        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst().keyPrefix()).hasSize(10);
        assertThat(keys.getFirst().label()).isEqualTo("my key");
    }

    @Test
    void revoke_wrongOwner_leavesKeyActive() {
        String rawKey = apiKeyService.create(userId, "my key");
        Long keyId = apiKeyService.listForActor(userId).getFirst().id();
        User otherUser = UserTestFixtures.createTestUser(userRepository, "Not The Owner", "svcnotowner-" + UUID.randomUUID() + "@example.com");

        apiKeyService.revoke(otherUser.getId(), keyId);

        assertThat(apiKeyService.resolveActorId(rawKey)).contains(userId);
    }

    @Test
    void deleteAllForActor_removesOnlyThatActorsKeys() {
        apiKeyService.create(userId, "my key");
        User otherUser = UserTestFixtures.createTestUser(userRepository, "Other User", "svcother-" + UUID.randomUUID() + "@example.com");
        apiKeyService.create(otherUser.getId(), "other key");

        apiKeyService.deleteAllForActor(userId);

        assertThat(apiKeyService.listForActor(userId)).isEmpty();
        assertThat(apiKeyService.listForActor(otherUser.getId())).hasSize(1);
    }
}
