package org.ost.integrationtests.level1.advertisement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.advertisement.config.AdvertisementAutoConfiguration;
import org.ost.advertisement.entity.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.core.model.SqlPredicate;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers repository test of {@link AdvertisementRepository}'s dynamic-SQL paths against real production SQL, booting the
 * user starter too because {@code AdvertisementAutoConfiguration} has {@code @DependsOn("userLiquibase")}.
 */
@SpringBootTest(classes = {
        AdvertisementAutoConfiguration.class,
        UserAutoConfiguration.class,
        RepositoryTestSupport.class
})
class AdvertisementRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AdvertisementRepository advertisementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryTestSupport.MutableAuditorAware auditorAware;

    @Autowired
    private JdbcClient jdbcClient;

    private Long actorId;

    /** Cleans every table and creates the actor and the auditor before each test, because the cached Spring context and its database are shared across test methods. */
    @BeforeEach
    void cleanDatabaseAndCreateActor() {
        TestDataCleaner.cleanAll(jdbcClient);

        User actor = UserTestFixtures.createTestUser(userRepository, "Test Actor",
                "actor-" + UUID.randomUUID() + "@example.com");
        actorId = actor.getId();
        auditorAware.setCurrentUserId(actorId);
    }

    private Advertisement save(String title, String description) {
        return advertisementRepository.save(Advertisement.builder()
                .title(title)
                .description(description)
                .adKind(AdKind.OFFER)
                .build());
    }

    @Test
    void save_and_findAdvertisementById_returnsPersistedRow() {
        Advertisement saved = save("Test title", "Test description");

        Optional<AdvertisementInfoDto> found = advertisementRepository.findAdvertisementById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Test title");
        assertThat(found.get().getDescription()).isEqualTo("Test description");
        assertThat(found.get().getCreatedBy()).isEqualTo(actorId);
        assertThat(found.get().getVersion()).isZero();
    }

    @Test
    void findByCreator_returnsOnlyActiveAdsForThatUser() {
        Advertisement mine = save("Mine", "Desc");
        User other = UserTestFixtures.createTestUser(userRepository, "Other Actor",
                "other-" + UUID.randomUUID() + "@example.com");
        auditorAware.setCurrentUserId(other.getId());
        save("Someone else's", "Desc");
        auditorAware.setCurrentUserId(actorId);
        Advertisement deletedOfMine = save("Deleted mine", "Desc");
        advertisementRepository.softDelete(deletedOfMine.getId(), actorId, deletedOfMine.getVersion());

        List<AdvertisementInfoDto> result = advertisementRepository.findByCreator(actorId);

        assertThat(result).extracting(AdvertisementInfoDto::getId).containsExactly(mine.getId());
    }

    @Test
    void findByFilter_titleFilter_returnsOnlyMatchingRows() {
        save("Vintage bicycle", "A red one");
        save("Modern bicycle", "A blue one");
        save("Old car", "Not a bicycle");

        List<AdvertisementInfoDto> results = advertisementRepository.findByFilter(
                AdvertisementFilterDto.builder().title("bicycle").build(),
                PageRequest.of(0, 10), null);

        assertThat(results).extracting(AdvertisementInfoDto::getTitle)
                .containsExactlyInAnyOrder("Vintage bicycle", "Modern bicycle");
    }

    @Test
    void findByFilter_emptyFilter_returnsAllRows() {
        save("First", "d1");
        save("Second", "d2");

        List<AdvertisementInfoDto> results = advertisementRepository.findByFilter(
                AdvertisementFilterDto.empty(), PageRequest.of(0, 10), null);

        assertThat(results).hasSize(2);
    }

    /** A taxon predicate restricts the page to the advertisements it matches. */
    @Test
    void findByFilter_taxonPredicateRestrictsToMatchingRows() {
        Advertisement first = save("First", "d1");
        Advertisement second = save("Second", "d2");
        save("Third", "d3");

        List<AdvertisementInfoDto> results = advertisementRepository.findByFilter(
                AdvertisementFilterDto.empty(), PageRequest.of(0, 10),
                new SqlPredicate("a.id = ANY(:_ids)", Map.of("_ids", new Long[]{first.getId(), second.getId()})));

        assertThat(results).extracting(AdvertisementInfoDto::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void countByFilter_taxonPredicateRestrictsCount() {
        Advertisement first = save("First", "d1");
        save("Second", "d2");
        save("Third", "d3");

        Long count = advertisementRepository.countByFilter(AdvertisementFilterDto.empty(),
                new SqlPredicate("a.id = ANY(:_ids)", Map.of("_ids", new Long[]{first.getId()})));

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void findByFilter_predicateWithOrIsParenthesisedSoTheConsumerFilterStillApplies() {
        Advertisement a = save("Vintage bicycle", "d1");
        Advertisement b = save("Old car", "d2");
        save("Vintage car", "d3");

        List<AdvertisementInfoDto> results = advertisementRepository.findByFilter(
                AdvertisementFilterDto.builder().title("Vintage").build(), PageRequest.of(0, 10),
                new SqlPredicate("a.id = :_one OR a.id = :_two", Map.of("_one", b.getId(), "_two", a.getId())));

        assertThat(results).extracting(AdvertisementInfoDto::getId).containsExactly(a.getId());
    }

    @Test
    void findByFilter_sortByTitle_ordersAscending() {
        save("Charlie", "d");
        save("Alpha", "d");
        save("Bravo", "d");

        List<AdvertisementInfoDto> results = advertisementRepository.findByFilter(
                AdvertisementFilterDto.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc(AdvertisementInfoDto.Fields.title))),
                null);

        assertThat(results).extracting(AdvertisementInfoDto::getTitle)
                .containsExactly("Alpha", "Bravo", "Charlie");
    }

    @Test
    void findByFilter_sortByCreatedAt_tiedRows_usesIdAsStableTiebreaker() {
        Advertisement first = save("First", "d1");
        Advertisement second = save("Second", "d2");
        jdbcClient.sql("UPDATE advertisement SET created_at = :ts WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource()
                        .addValue("ts", OffsetDateTime.now(ZoneOffset.UTC))
                        .addValue("ids", new Long[]{first.getId(), second.getId()}))
                .update();

        List<AdvertisementInfoDto> results = advertisementRepository.findByFilter(
                AdvertisementFilterDto.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc(AdvertisementInfoDto.Fields.createdAt))),
                null);

        assertThat(results).extracting(AdvertisementInfoDto::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void findByFilter_sortByUpdatedAt_tiedRows_usesIdAsStableTiebreaker() {
        Advertisement first = save("First", "d1");
        Advertisement second = save("Second", "d2");
        jdbcClient.sql("UPDATE advertisement SET updated_at = :ts WHERE id = ANY(:ids)")
                .paramSource(new MapSqlParameterSource()
                        .addValue("ts", OffsetDateTime.now(ZoneOffset.UTC))
                        .addValue("ids", new Long[]{first.getId(), second.getId()}))
                .update();

        List<AdvertisementInfoDto> results = advertisementRepository.findByFilter(
                AdvertisementFilterDto.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc(AdvertisementInfoDto.Fields.updatedAt))),
                null);

        assertThat(results).extracting(AdvertisementInfoDto::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void findByFilter_pagination_respectsLimitAndOffset() {
        save("A", "d");
        save("B", "d");
        save("C", "d");

        List<AdvertisementInfoDto> firstPage = advertisementRepository.findByFilter(
                AdvertisementFilterDto.empty(),
                PageRequest.of(0, 2, Sort.by(Sort.Order.asc(AdvertisementInfoDto.Fields.title))),
                null);
        List<AdvertisementInfoDto> secondPage = advertisementRepository.findByFilter(
                AdvertisementFilterDto.empty(),
                PageRequest.of(1, 2, Sort.by(Sort.Order.asc(AdvertisementInfoDto.Fields.title))),
                null);

        assertThat(firstPage).extracting(AdvertisementInfoDto::getTitle).containsExactly("A", "B");
        assertThat(secondPage).extracting(AdvertisementInfoDto::getTitle).containsExactly("C");
    }

    @Test
    void save_staleVersion_throwsStaleWriteException() {
        Advertisement saved = save("Original", "d");
        advertisementRepository.save(Advertisement.builder()
                .id(saved.getId())
                .title("First update")
                .description(saved.getDescription())
                .adKind(saved.getAdKind())
                .createdAt(saved.getCreatedAt())
                .createdBy(saved.getCreatedBy())
                .version(saved.getVersion())
                .build());

        Advertisement staleUpdate = Advertisement.builder()
                .id(saved.getId())
                .title("Stale update")
                .description(saved.getDescription())
                .adKind(saved.getAdKind())
                .createdAt(saved.getCreatedAt())
                .createdBy(saved.getCreatedBy())
                .version(saved.getVersion())
                .build();

        assertThatThrownBy(() -> advertisementRepository.save(staleUpdate))
                .isInstanceOf(StaleWriteException.class);
    }

    @Test
    void softDelete_staleVersion_throwsStaleWriteException() {
        Advertisement saved = save("To be deleted", "d");

        assertThatThrownBy(() -> advertisementRepository.softDelete(saved.getId(), actorId, saved.getVersion() + 1))
                .isInstanceOf(StaleWriteException.class);
    }

    @Test
    void softDelete_currentVersion_succeedsAndExcludesRowFromFilter() {
        Advertisement saved = save("To be deleted", "d");

        advertisementRepository.softDelete(saved.getId(), actorId, saved.getVersion());

        assertThat(advertisementRepository.findAdvertisementById(saved.getId())).isEmpty();
    }
}
