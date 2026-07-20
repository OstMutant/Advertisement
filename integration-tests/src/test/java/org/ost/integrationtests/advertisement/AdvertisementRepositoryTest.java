package org.ost.integrationtests.advertisement;

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
 * Batch 1 exemplar: proves the Testcontainers repository-test pattern end-to-end against real
 * production SQL (not a mock, not a stub schema) — the highest-risk dynamic-SQL paths in
 * {@link AdvertisementRepository}, not exhaustive coverage. Boots both
 * {@code advertisement-spring-boot-starter} and {@code user-spring-boot-starter}'s real
 * autoconfiguration in one Spring context (satisfying {@code AdvertisementAutoConfiguration}'s
 * {@code @DependsOn("userLiquibase")} and the FK from {@code advertisement.created_by} to
 * {@code user_information.id}) — see {@code integration-tests/CLAUDE.md}.
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

    /** The Spring context (and its database) is cached and reused across every test method in
     *  this class — clean both tables before each test so assertions on row counts/contents
     *  never see another test method's leftover data. Deletes advertisement before
     *  user_information to respect the FK. */
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

    /**
     * Covers improvement-050 item 2: {@code buildIdClause()} used to bind {@code allowedIds} as a
     * {@code Set}, which Spring expands into one {@code ?} placeholder per element for
     * {@code IN (:allowedIds)} — unbounded for a popular category's advertisement count. Fixed by
     * binding a plain array instead ({@code = ANY(:allowedIds)}, matching {@code
     * findExistingIds()}'s already-proven pattern in this class), so Spring passes it through as a
     * single JDBC parameter regardless of size.
     */
    @Test
    void findByFilter_allowedIdsRestrictsToMatchingRows() {
        Advertisement first = save("First", "d1");
        Advertisement second = save("Second", "d2");
        save("Third", "d3");

        List<AdvertisementInfoDto> results = advertisementRepository.findByFilter(
                AdvertisementFilterDto.empty(), PageRequest.of(0, 10),
                Set.of(first.getId(), second.getId()));

        assertThat(results).extracting(AdvertisementInfoDto::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void countByFilter_allowedIdsRestrictsCount() {
        Advertisement first = save("First", "d1");
        save("Second", "d2");
        save("Third", "d3");

        Long count = advertisementRepository.countByFilter(AdvertisementFilterDto.empty(), Set.of(first.getId()));

        assertThat(count).isEqualTo(1L);
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
    void softDelete_staleVersion_throwsOptimisticLockingFailureException() {
        Advertisement saved = save("To be deleted", "d");

        assertThatThrownBy(() -> advertisementRepository.softDelete(saved.getId(), actorId, saved.getVersion() + 1))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void softDelete_currentVersion_succeedsAndExcludesRowFromFilter() {
        Advertisement saved = save("To be deleted", "d");

        advertisementRepository.softDelete(saved.getId(), actorId, saved.getVersion());

        assertThat(advertisementRepository.findAdvertisementById(saved.getId())).isEmpty();
    }
}
