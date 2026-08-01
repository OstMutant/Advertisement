package org.ost.integrationtests.providerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.provider.config.ProviderProfileAutoConfiguration;
import org.ost.provider.entity.ProviderProfile;
import org.ost.provider.repository.ProviderProfileRepository;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.entity.User;
import org.ost.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers repository test for {@link ProviderProfileRepository} — mirrors {@code
 * AdvertisementRepositoryTest}'s shape. Boots both {@code provider-profile-spring-boot-starter}
 * and {@code user-spring-boot-starter}'s real autoconfiguration in one Spring context (satisfying
 * {@code ProviderProfileAutoConfiguration}'s {@code @DependsOn("userLiquibase")}) — see
 * {@code integration-tests/CLAUDE.md}.
 */
@SpringBootTest(classes = {
        ProviderProfileAutoConfiguration.class,
        UserAutoConfiguration.class,
        RepositoryTestSupport.class
})
class ProviderProfileRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryTestSupport.MutableAuditorAware auditorAware;

    @Autowired
    private JdbcClient jdbcClient;

    private Long actorId;

    @BeforeEach
    void cleanDatabaseAndCreateActor() {
        TestDataCleaner.cleanAll(jdbcClient);

        User actor = UserTestFixtures.createTestUser(userRepository, "Test Actor",
                "actor-" + UUID.randomUUID() + "@example.com");
        actorId = actor.getId();
        auditorAware.setCurrentUserId(actorId);
    }

    private ProviderProfile save(Long actorId, ProviderKind kind) {
        return providerProfileRepository.save(ProviderProfile.builder()
                .actorId(actorId)
                .kind(kind)
                .about("About text")
                .build());
    }

    private Long newActor() {
        return UserTestFixtures.createTestUser(userRepository, "Actor " + UUID.randomUUID(),
                "actor-" + UUID.randomUUID() + "@example.com").getId();
    }

    @Test
    void save_and_findProviderProfileById_returnsPersistedRow() {
        ProviderProfile saved = save(actorId, ProviderKind.MASTER);

        Optional<ProviderProfileDto> found = providerProfileRepository.findProviderProfileById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getActorId()).isEqualTo(actorId);
        assertThat(found.get().getKind()).isEqualTo(ProviderKind.MASTER);
        assertThat(found.get().getAbout()).isEqualTo("About text");
        assertThat(found.get().getVersion()).isZero();
    }

    @Test
    void findByActorId_returnsRowForThatActor() {
        ProviderProfile saved = save(actorId, ProviderKind.SHOP);

        Optional<ProviderProfileDto> found = providerProfileRepository.findByActorId(actorId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByActorId_noProfile_returnsEmpty() {
        assertThat(providerProfileRepository.findByActorId(actorId)).isEmpty();
    }

    @Test
    void findByFilter_kindsFilter_returnsOnlyMatchingRows() {
        save(actorId, ProviderKind.MASTER);
        save(newActor(), ProviderKind.SHOP);
        save(newActor(), ProviderKind.SUPPORT);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.builder().kinds(Set.of(ProviderKind.MASTER, ProviderKind.SHOP)).build(),
                PageRequest.of(0, 10), null);

        assertThat(results).extracting(ProviderProfileDto::getKind)
                .containsExactlyInAnyOrder(ProviderKind.MASTER, ProviderKind.SHOP);
    }

    @Test
    void findByFilter_cityTaxonIdFilter_returnsOnlyMatchingRows() {
        ProviderProfile inCity = providerProfileRepository.save(ProviderProfile.builder()
                .actorId(actorId).kind(ProviderKind.MASTER).cityTaxonId(7L).build());
        providerProfileRepository.save(ProviderProfile.builder()
                .actorId(newActor()).kind(ProviderKind.SHOP).cityTaxonId(8L).build());

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.builder().cityTaxonId(7L).build(),
                PageRequest.of(0, 10), null);

        assertThat(results).extracting(ProviderProfileDto::getId).containsExactly(inCity.getId());
    }

    @Test
    void findByFilter_emptyFilter_returnsAllRows() {
        save(actorId, ProviderKind.MASTER);
        save(newActor(), ProviderKind.SHOP);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(), PageRequest.of(0, 10), null);

        assertThat(results).hasSize(2);
    }

    @Test
    void findByFilter_allowedIdsRestrictsToMatchingRows() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        save(newActor(), ProviderKind.SUPPORT);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(), PageRequest.of(0, 10),
                Set.of(first.getId(), second.getId()));

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void countByFilter_allowedIdsRestrictsCount() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        save(newActor(), ProviderKind.SHOP);

        Long count = providerProfileRepository.countByFilter(ProviderProfileFilterDto.empty(), Set.of(first.getId()));

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void findByFilter_pagination_respectsLimitAndOffset() {
        save(actorId, ProviderKind.MASTER);
        save(newActor(), ProviderKind.SHOP);
        save(newActor(), ProviderKind.SUPPORT);

        List<ProviderProfileDto> firstPage = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(0, 2, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.id))),
                null);
        List<ProviderProfileDto> secondPage = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(1, 2, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.id))),
                null);

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
    }

    @Test
    void delete_staleVersion_throwsOptimisticLockingFailureException() {
        ProviderProfile saved = save(actorId, ProviderKind.MASTER);

        assertThatThrownBy(() -> providerProfileRepository.delete(saved.getId(), saved.getVersion() + 1))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void delete_currentVersion_succeedsAndRemovesRow() {
        ProviderProfile saved = save(actorId, ProviderKind.MASTER);

        providerProfileRepository.delete(saved.getId(), saved.getVersion());

        assertThat(providerProfileRepository.findProviderProfileById(saved.getId())).isEmpty();
    }

    @Test
    void findOwnerIds_returnsActorsWithProfile() {
        save(actorId, ProviderKind.MASTER);
        Long otherActor = newActor();

        Set<Long> ownerIds = providerProfileRepository.findOwnerIds(Set.of(actorId, otherActor));

        assertThat(ownerIds).containsExactly(actorId);
    }

    @Test
    void findExistingIds_returnsOnlyPersistedIds() {
        ProviderProfile saved = save(actorId, ProviderKind.MASTER);

        List<Long> existing = providerProfileRepository.findExistingIds(new Long[]{saved.getId(), saved.getId() + 1000});

        assertThat(existing).containsExactly(saved.getId());
    }
}
