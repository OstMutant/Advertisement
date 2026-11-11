package org.ost.integrationtests.level2.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.contact.config.ContactAutoConfiguration;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.orchestrator.config.OrchestratorAutoConfiguration;
import org.ost.orchestrator.services.ContactAccessService;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.orchestrator.spi.CurrentUserHook;
import org.ost.orchestrator.spi.SessionActorHook;
import org.ost.orchestrator.spi.SettingsChangeHook;
import org.ost.orchestrator.spi.UiLabelHook;
import org.ost.orchestrator.spi.UiLocaleHook;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.promotion.config.PromotionAutoConfiguration;
import org.ost.provider.config.ProviderProfileAutoConfiguration;
import org.ost.provider.entity.ProviderProfile;
import org.ost.provider.repository.ProviderProfileRepository;
import org.ost.taxon.config.TaxonAutoConfiguration;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Level 2: deleting a provider profile is one all-or-nothing unit -- when the delete is rejected or a later step fails, the
 * profile, its contact and its reviews, replies and rating summary are all still there.
 */
@SpringBootTest(classes = {
        UserAutoConfiguration.class,
        TaxonAutoConfiguration.class,
        ProviderProfileAutoConfiguration.class,
        ContactAutoConfiguration.class,
        FeedbackAutoConfiguration.class,
        PromotionAutoConfiguration.class,
        OrchestratorAutoConfiguration.class,
        RepositoryTestSupport.class
})
@TestPropertySource(properties = {"spring.datasource.hikari.maximum-pool-size=2", "app.public-base-url=http://localhost:8080"})
class ProviderProfileDeleteTransactionTest extends AbstractPostgresIntegrationTest {

    // The application shell implements these hooks in production; AuditPort is mocked so a test can make its call fail.
    @MockitoBean private AuditPort auditPort;
    @MockitoBean private UiLabelHook uiLabelHook;
    @MockitoBean private UiLocaleHook uiLocaleHook;
    @MockitoBean private CurrentUserHook currentUserHook;
    @MockitoBean private SessionActorHook sessionActorHook;
    @MockitoBean private SettingsChangeHook settingsChangeHook;

    @Autowired private ProviderProfileSaveService providerProfileSaveService;
    @Autowired private FeedbackAccessService feedbackAccessService;
    @Autowired private ContactAccessService contactAccessService;
    @Autowired private ProviderProfileRepository providerProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcClient jdbcClient;

    /** Identifies a test provider profile by its owner id, profile id, version and the id of its one review. */
    private record Profile(Long ownerId, Long id, Long version, Long reviewId) {
    }

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private Long newUser(String name) {
        return UserTestFixtures.createTestUser(userRepository, name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com").getId();
    }

    private Profile newProfileWithContactReviewAndReply() {
        Long ownerId = newUser("Owner");
        ProviderProfile saved = providerProfileRepository.save(ProviderProfile.builder()
                .actorId(ownerId)
                .kind(ProviderKind.MASTER)
                .about("About")
                .build());
        contactAccessService.save(new ContactInfoDto(null, EntityType.PROVIDER_PROFILE, saved.getId(), "+380501234567", null, null, null, null));
        FeedbackDto review = feedbackAccessService.save(
                new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, saved.getId(), newUser("Author"), 4, "Nice!", null));
        feedbackAccessService.saveComment(new FeedbackCommentSaveDto(null, review.id(), ownerId, "Thanks", null));
        return new Profile(ownerId, saved.getId(), saved.getVersion(), review.id());
    }

    private void assertEverythingIsStillThere(Profile profile) {
        assertThat(providerProfileSaveService.findById(profile.id())).isPresent();
        assertThat(contactAccessService.find(EntityType.PROVIDER_PROFILE, profile.id())).isPresent();
        assertThat(feedbackAccessService.findFeedbackById(profile.reviewId())).isPresent();
        assertThat(feedbackAccessService.findCommentByFeedback(profile.reviewId())).isPresent();
        assertThat(feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, profile.id()).reviewCount()).isEqualTo(1);
    }

    private void assertEverythingIsGone(Profile profile) {
        assertThat(providerProfileSaveService.findById(profile.id())).isEmpty();
        assertThat(contactAccessService.find(EntityType.PROVIDER_PROFILE, profile.id())).isEmpty();
        assertThat(feedbackAccessService.findFeedbackById(profile.reviewId())).isEmpty();
        assertThat(feedbackAccessService.findCommentByFeedback(profile.reviewId())).isEmpty();
        assertThat(feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, profile.id()).reviewCount()).isZero();
    }

    @Test
    void delete_withAStaleVersion_changesNothing() {
        Profile profile = newProfileWithContactReviewAndReply();

        assertThatThrownBy(() -> providerProfileSaveService.delete(profile.id(), profile.ownerId(), profile.version() + 1))
                .isInstanceOf(StaleWriteException.class);

        assertEverythingIsStillThere(profile);
    }

    @Test
    void delete_whenALaterStepFails_changesNothing() {
        Profile profile = newProfileWithContactReviewAndReply();
        doThrow(new IllegalStateException("audit down")).when(auditPort).captureDeletion(any(), any(), any());

        assertThatThrownBy(() -> providerProfileSaveService.delete(profile.id(), profile.ownerId(), profile.version()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit down");

        assertEverythingIsStillThere(profile);
    }

    @Test
    void delete_withTheCurrentVersion_removesTheProfileItsContactAndItsFeedback() {
        Profile profile = newProfileWithContactReviewAndReply();

        providerProfileSaveService.delete(profile.id(), profile.ownerId(), profile.version());

        assertEverythingIsGone(profile);
    }
}
