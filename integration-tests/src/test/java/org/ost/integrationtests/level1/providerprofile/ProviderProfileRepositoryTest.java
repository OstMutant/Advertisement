package org.ost.integrationtests.level1.providerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileScope;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.provider.config.ProviderProfileAutoConfiguration;
import org.ost.provider.entity.ProviderProfile;
import org.ost.provider.repository.ProviderProfileRepository;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.entity.User;
import org.ost.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.ost.platform.core.StaleWriteException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testcontainers repository test for {@link ProviderProfileRepository}; boots the user starter too, since {@code ProviderProfileAutoConfiguration} depends on {@code userLiquibase}. */
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
                PageRequest.of(0, 10), ProviderProfileScope.ALL);

        assertThat(results).extracting(ProviderProfileDto::getKind)
                .containsExactlyInAnyOrder(ProviderKind.MASTER, ProviderKind.SHOP);
    }

    @Test
    void findByFilter_emptyFilter_returnsAllRows() {
        save(actorId, ProviderKind.MASTER);
        save(newActor(), ProviderKind.SHOP);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(), PageRequest.of(0, 10), ProviderProfileScope.ALL);

        assertThat(results).hasSize(2);
    }

    @Test
    void findByFilter_allowedIdsRestrictsToMatchingRows() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        save(newActor(), ProviderKind.SUPPORT);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(), PageRequest.of(0, 10),
                new ProviderProfileScope(Set.of(first.getId(), second.getId()), List.of(), false));

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void countByFilter_allowedIdsRestrictsCount() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        save(newActor(), ProviderKind.SHOP);

        Long count = providerProfileRepository.countByFilter(ProviderProfileFilterDto.empty(),
                new ProviderProfileScope(Set.of(first.getId()), List.of(), false));

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void findByFilter_allowedIdsAndKindsFilter_returnsOnlyRowsMatchingBoth() {
        ProviderProfile master = save(actorId, ProviderKind.MASTER);
        ProviderProfile shop = save(newActor(), ProviderKind.SHOP);
        save(newActor(), ProviderKind.MASTER);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.builder().kinds(Set.of(ProviderKind.MASTER)).build(), PageRequest.of(0, 10),
                new ProviderProfileScope(Set.of(master.getId(), shop.getId()), List.of(), false));

        assertThat(results).extracting(ProviderProfileDto::getId).containsExactly(master.getId());
    }

    @Test
    void countByFilter_emptyFilterAndNoScope_countsAllRows() {
        save(actorId, ProviderKind.MASTER);
        save(newActor(), ProviderKind.SHOP);

        Long count = providerProfileRepository.countByFilter(ProviderProfileFilterDto.empty(), ProviderProfileScope.ALL);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void findByFilter_pinnedGroup_isListedFirstDespiteTheCallersSort() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        ProviderProfile third = save(newActor(), ProviderKind.SUPPORT);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.id))),
                new ProviderProfileScope(null, List.of(Set.of(third.getId())), false));

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactly(third.getId(), first.getId(), second.getId());
    }

    @Test
    void findByFilter_pinnedGroups_followTheGivenOrderThenTheCallersSort() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        ProviderProfile third = save(newActor(), ProviderKind.SUPPORT);
        ProviderProfile fourth = save(newActor(), ProviderKind.MASTER);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.id))),
                new ProviderProfileScope(null, List.of(Set.of(fourth.getId()), Set.of(second.getId(), third.getId())), false));

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactly(fourth.getId(), second.getId(), third.getId(), first.getId());
    }

    @Test
    void findByFilter_pinnedGroupWithoutAnyCallerSort_endsEachGroupWithTheNewestIdFirst() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        ProviderProfile third = save(newActor(), ProviderKind.SUPPORT);
        ProviderProfile fourth = save(newActor(), ProviderKind.MASTER);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(), PageRequest.of(0, 10, Sort.unsorted()),
                new ProviderProfileScope(null, List.of(Set.of(first.getId(), third.getId())), false));

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactly(third.getId(), first.getId(), fourth.getId(), second.getId());
    }

    @Test
    void findByFilter_noCallerSortAndNoPinnedGroup_stillHasAStableNewestIdFirstOrder() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        ProviderProfile third = save(newActor(), ProviderKind.SUPPORT);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(), PageRequest.of(0, 10, Sort.unsorted()), ProviderProfileScope.ALL);

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void findByFilter_pinnedAfterRest_listsThePinnedGroupLast() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        ProviderProfile third = save(newActor(), ProviderKind.SUPPORT);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.id))),
                new ProviderProfileScope(null, List.of(Set.of(first.getId())), true));

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactly(second.getId(), third.getId(), first.getId());
    }

    @Test
    void findByFilter_onlyIdsAndPinned_limitsFirstThenPins() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        save(newActor(), ProviderKind.SUPPORT);

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.id))),
                new ProviderProfileScope(Set.of(first.getId(), second.getId()), List.of(Set.of(second.getId())), false));

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void countByFilter_pinnedGroupsDoNotChangeTheCount() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        save(newActor(), ProviderKind.SHOP);

        Long count = providerProfileRepository.countByFilter(ProviderProfileFilterDto.empty(),
                new ProviderProfileScope(null, List.of(Set.of(first.getId())), false));

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void findByFilter_pagination_respectsLimitAndOffset() {
        save(actorId, ProviderKind.MASTER);
        save(newActor(), ProviderKind.SHOP);
        save(newActor(), ProviderKind.SUPPORT);

        List<ProviderProfileDto> firstPage = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(0, 2, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.id))),
                ProviderProfileScope.ALL);
        List<ProviderProfileDto> secondPage = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(1, 2, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.id))),
                ProviderProfileScope.ALL);

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
    }

    @Test
    void findByFilter_sortByCreatedAt_tiedRows_usesIdAsStableTiebreaker() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        jdbcClient.sql("UPDATE provider_profile SET created_at = :ts WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource()
                        .addValue("ts", OffsetDateTime.now(ZoneOffset.UTC))
                        .addValue("ids", new Long[]{first.getId(), second.getId()}))
                .update();

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.createdAt))),
                ProviderProfileScope.ALL);

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void findByFilter_sortByUpdatedAt_tiedRows_usesIdAsStableTiebreaker() {
        ProviderProfile first = save(actorId, ProviderKind.MASTER);
        ProviderProfile second = save(newActor(), ProviderKind.SHOP);
        jdbcClient.sql("UPDATE provider_profile SET updated_at = :ts WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource()
                        .addValue("ts", OffsetDateTime.now(ZoneOffset.UTC))
                        .addValue("ids", new Long[]{first.getId(), second.getId()}))
                .update();

        List<ProviderProfileDto> results = providerProfileRepository.findByFilter(
                ProviderProfileFilterDto.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.updatedAt))),
                ProviderProfileScope.ALL);

        assertThat(results).extracting(ProviderProfileDto::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void save_staleVersion_throwsStaleWriteException() {
        ProviderProfile saved = save(actorId, ProviderKind.MASTER);
        providerProfileRepository.save(ProviderProfile.builder()
                .id(saved.getId())
                .actorId(saved.getActorId())
                .kind(saved.getKind())
                .about("First update")
                .createdAt(saved.getCreatedAt())
                .version(saved.getVersion())
                .build());

        ProviderProfile staleUpdate = ProviderProfile.builder()
                .id(saved.getId())
                .actorId(saved.getActorId())
                .kind(saved.getKind())
                .about("Stale update")
                .createdAt(saved.getCreatedAt())
                .version(saved.getVersion())
                .build();

        assertThatThrownBy(() -> providerProfileRepository.save(staleUpdate))
                .isInstanceOf(StaleWriteException.class);
    }

    @Test
    void delete_staleVersion_throwsStaleWriteException() {
        ProviderProfile saved = save(actorId, ProviderKind.MASTER);

        assertThatThrownBy(() -> providerProfileRepository.delete(saved.getId(), saved.getVersion() + 1))
                .isInstanceOf(StaleWriteException.class);
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
