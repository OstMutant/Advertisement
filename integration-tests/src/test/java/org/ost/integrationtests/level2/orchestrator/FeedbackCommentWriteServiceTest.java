package org.ost.integrationtests.level2.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.ActorLookupService;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.FeedbackCommentWriteService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.UserActorNameService;
import org.ost.orchestrator.spi.UiLabelHook;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.provider.config.ProviderProfileAutoConfiguration;
import org.ost.provider.entity.ProviderProfile;
import org.ost.provider.repository.ProviderProfileRepository;
import org.ost.user.config.UserAutoConfiguration;
import org.ost.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Level 2: {@link FeedbackCommentWriteService}'s owner-check, composing real
 * {@code FeedbackPort}/{@code ProviderProfilePort}/{@code UserAuthorizationPort} through the real
 * orchestrator service -- this check lives in the orchestrator, not in {@code FeedbackService}
 * itself, so it needs a real multi-starter Spring context to exercise, not a Level 1 repository
 * test. Lists only the exact orchestrator service classes this test needs directly (not
 * {@code OrchestratorAutoConfiguration}, whose package-wide component scan would pull in every
 * other orchestrator service and their own unrelated bean requirements).
 */
@SpringBootTest(classes = {
        FeedbackAutoConfiguration.class,
        ProviderProfileAutoConfiguration.class,
        UserAutoConfiguration.class,
        RepositoryTestSupport.class,
        ValidationAutoConfiguration.class,
        ActorLookupService.class,
        UserActorNameService.class,
        ProviderProfileReadService.class,
        AuthorizationService.class,
        FeedbackAccessService.class,
        FeedbackCommentWriteService.class,
        FeedbackCommentWriteServiceTest.StubUiLabelHookConfig.class
})
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class FeedbackCommentWriteServiceTest extends AbstractPostgresIntegrationTest {

    /** {@link UiLabelHook} is only implemented in marketplace-app in production (not a dependency
     *  of integration-tests) -- this stub satisfies UserActorNameService's transitive dependency. */
    @TestConfiguration
    static class StubUiLabelHookConfig {
        @Bean
        UiLabelHook uiLabelHook() {
            return new UiLabelHook() {
                @Override
                public String translateActorDeletedSuffix(String actorName) {
                    return actorName;
                }

                @Override
                public String labelFor(AdKind kind) {
                    return kind.name();
                }

                @Override
                public String markDeleted(String name) {
                    return name;
                }

                @Override
                public String noMediaPlaceholder() {
                    return "";
                }
            };
        }
    }

    @Autowired
    private FeedbackCommentWriteService feedbackCommentWriteService;

    @Autowired
    private FeedbackPort feedbackPort;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private Long newUser(String name) {
        return UserTestFixtures.createTestUser(userRepository, name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com").getId();
    }

    private Long newProviderProfile(Long ownerId) {
        return providerProfileRepository.save(ProviderProfile.builder()
                .actorId(ownerId)
                .kind(ProviderKind.MASTER)
                .about("About")
                .build()).getId();
    }

    private FeedbackDto newFeedback(Long authorId, Long providerProfileId) {
        return feedbackPort.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, providerProfileId, authorId, 5, "Great!", null));
    }

    @Test
    void saveComment_byProfileOwner_succeeds() {
        Long ownerId = newUser("Owner");
        Long providerProfileId = newProviderProfile(ownerId);
        Long reviewerId = newUser("Reviewer");
        FeedbackDto feedback = newFeedback(reviewerId, providerProfileId);

        FeedbackCommentDto reply = feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "Thanks!", null));

        assertThat(reply.authorId()).isEqualTo(ownerId);
        assertThat(reply.commentText()).isEqualTo("Thanks!");
    }

    @Test
    void saveComment_byNonOwner_throwsAccessDeniedException() {
        Long ownerId = newUser("Owner");
        Long providerProfileId = newProviderProfile(ownerId);
        Long reviewerId = newUser("Reviewer");
        FeedbackDto feedback = newFeedback(reviewerId, providerProfileId);
        Long strangerId = newUser("Stranger");

        assertThatThrownBy(() -> feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), strangerId, "Not mine to answer.", null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void saveComment_secondReplyOnSameFeedback_throwsBecauseOnlyOneReplyAllowed() {
        Long ownerId = newUser("Owner");
        Long providerProfileId = newProviderProfile(ownerId);
        Long reviewerId = newUser("Reviewer");
        FeedbackDto feedback = newFeedback(reviewerId, providerProfileId);
        feedbackCommentWriteService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "First reply.", null));

        assertThatThrownBy(() -> feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "Second reply attempt.", null)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void saveComment_editByOriginalAuthor_succeeds() {
        Long ownerId = newUser("Owner");
        Long providerProfileId = newProviderProfile(ownerId);
        Long reviewerId = newUser("Reviewer");
        FeedbackDto feedback = newFeedback(reviewerId, providerProfileId);
        FeedbackCommentDto reply = feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "First reply.", null));

        FeedbackCommentDto updated = feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(reply.id(), feedback.id(), ownerId, "Edited reply.", reply.version()));

        assertThat(updated.commentText()).isEqualTo("Edited reply.");
    }

    @Test
    void saveComment_editByAdminNotAuthor_throwsAccessDeniedException_noOverride() {
        Long ownerId = newUser("Owner");
        Long providerProfileId = newProviderProfile(ownerId);
        Long reviewerId = newUser("Reviewer");
        FeedbackDto feedback = newFeedback(reviewerId, providerProfileId);
        FeedbackCommentDto reply = feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "First reply.", null));
        Long adminId = newUser("Admin");

        assertThatThrownBy(() -> feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(reply.id(), feedback.id(), adminId, "Admin edit attempt.", reply.version())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteComment_byNonAuthor_throwsAccessDeniedException() {
        Long ownerId = newUser("Owner");
        Long providerProfileId = newProviderProfile(ownerId);
        Long reviewerId = newUser("Reviewer");
        FeedbackDto feedback = newFeedback(reviewerId, providerProfileId);
        FeedbackCommentDto reply = feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "First reply.", null));

        assertThatThrownBy(() -> feedbackCommentWriteService.deleteComment(reply.id(), reviewerId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteComment_byAuthor_succeeds() {
        Long ownerId = newUser("Owner");
        Long providerProfileId = newProviderProfile(ownerId);
        Long reviewerId = newUser("Reviewer");
        FeedbackDto feedback = newFeedback(reviewerId, providerProfileId);
        FeedbackCommentDto reply = feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "First reply.", null));

        feedbackCommentWriteService.deleteComment(reply.id(), ownerId);

        assertThat(feedbackPort.findCommentByFeedback(feedback.id())).isEmpty();
    }
}
