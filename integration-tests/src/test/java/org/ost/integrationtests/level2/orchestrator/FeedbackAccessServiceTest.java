package org.ost.integrationtests.level2.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ost.feedback.config.FeedbackAutoConfiguration;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.UserTestFixtures;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.orchestrator.dto.ModerationQueueFilterDto;
import org.ost.orchestrator.dto.ModerationQueueItemDto;
import org.ost.orchestrator.dto.ModerationQueueItemType;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.ActorLookupService;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.UserActorNameService;
import org.ost.orchestrator.spi.UiLabelHook;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.user.model.Role;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Level 2: {@link FeedbackAccessService}'s own public API -- the merged moderation queue
 * (filter/sort/status transitions) is real cross-source business logic composed here, not a
 * thin pass-through, so it needs a real multi-starter Spring context to exercise, not a Level 1
 * repository test.
 */
@SpringBootTest(classes = {
        FeedbackAutoConfiguration.class,
        ProviderProfileAutoConfiguration.class,
        UserAutoConfiguration.class,
        RepositoryTestSupport.class,
        ValidationAutoConfiguration.class,
        ActorLookupService.class,
        AuthorizationService.class,
        UserActorNameService.class,
        ProviderProfileReadService.class,
        FeedbackAccessService.class,
        FeedbackAccessServiceTest.StubUiLabelHookConfig.class
})
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class FeedbackAccessServiceTest extends AbstractPostgresIntegrationTest {

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

    @MockitoBean
    private AuditPort auditPort;

    @Autowired
    private FeedbackAccessService feedbackAccessService;

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

    private Long newModerator(String name) {
        return UserTestFixtures.createTestUser(userRepository, name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com", Role.MODERATOR).getId();
    }

    private Long newProviderProfile(Long ownerId) {
        return providerProfileRepository.save(ProviderProfile.builder()
                .actorId(ownerId)
                .kind(ProviderKind.MASTER)
                .about("About")
                .build()).getId();
    }

    @Test
    void isAvailable_returnsTrueWhenFeedbackStarterPresent() {
        assertThat(feedbackAccessService.isAvailable()).isTrue();
    }

    @Test
    void save_findForEntity_getAggregate_findFeedbackById() {
        Long authorId = newUser("Author");
        Long profileId = newProviderProfile(newUser("Owner"));

        FeedbackDto saved = feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, authorId, 4, "Nice!", null));
        assertThat(saved.id()).isNotNull();

        assertThat(feedbackAccessService.findForEntity(EntityType.PROVIDER_PROFILE, profileId, 0, 20))
                .extracting(FeedbackDto::id).contains(saved.id());

        FeedbackAggregateDto agg = feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, profileId);
        assertThat(agg.reviewCount()).isEqualTo(1);

        assertThat(feedbackAccessService.findFeedbackById(saved.id())).isPresent();
    }

    @Test
    void getAggregate_noFeedback_returnsZeroDefault() {
        FeedbackAggregateDto agg = feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, 999999L);
        assertThat(agg.reviewCount()).isEqualTo(0);
    }

    @Test
    void saveComment_findCommentByFeedback_findCommentById_deleteComment() {
        Long authorId = newUser("Author");
        Long ownerId = newUser("Owner");
        Long profileId = newProviderProfile(ownerId);
        FeedbackDto feedback = feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, authorId, 5, "Great", null));

        FeedbackCommentDto comment = feedbackAccessService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "Thanks", null));
        assertThat(feedbackAccessService.findCommentByFeedback(feedback.id())).isPresent();
        assertThat(feedbackAccessService.findCommentById(comment.id())).isPresent();

        feedbackAccessService.deleteComment(comment.id());
        assertThat(feedbackAccessService.findCommentByFeedback(feedback.id())).isEmpty();
    }

    @Test
    void resolveAuthorNames_returnsRealNames() {
        Long userId = newUser("Namey");
        Map<Long, String> names = feedbackAccessService.resolveAuthorNames(Set.of(userId));
        assertThat(names).containsKey(userId);
    }

    @Test
    void moderationQueue_flagApproveConfirmReject_fullLifecycle() {
        Long authorId = newUser("Author2");
        Long ownerId = newUser("Owner2");
        Long profileId = newProviderProfile(ownerId);
        Long actorId = newModerator("Moderator2");

        FeedbackDto feedback = feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, authorId, 1, "Bad", null));
        FeedbackCommentDto comment = feedbackAccessService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "Reply", null));

        feedbackAccessService.flagFeedback(feedback.id(), actorId, "Spam");
        feedbackAccessService.flagComment(comment.id(), actorId, "Rude");

        assertThat(feedbackAccessService.findHiddenFeedback(PageRequest.of(0, 10))).extracting(FeedbackDto::id).contains(feedback.id());
        assertThat(feedbackAccessService.findHiddenComments(PageRequest.of(0, 10))).extracting(FeedbackCommentDto::id).contains(comment.id());

        FeedbackAccessService.ModerationQueuePage page = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.empty(), Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), 0, 20);
        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.items()).extracting(ModerationQueueItemDto::getType)
                .contains(ModerationQueueItemType.FEEDBACK, ModerationQueueItemType.COMMENT);

        FeedbackAccessService.ModerationQueuePage commentOnly = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.builder().types(Set.of(ModerationQueueItemType.COMMENT)).build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), 0, 20);
        assertThat(commentOnly.totalCount()).isEqualTo(1);

        FeedbackAccessService.ModerationQueuePage sortedByAuthor = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.empty(), Sort.by(Sort.Order.asc(ModerationQueueItemDto.Fields.authorName)), 0, 20);
        assertThat(sortedByAuthor.items()).hasSize(2);

        FeedbackAccessService.ModerationQueuePage sortedByType = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.empty(), Sort.by(Sort.Order.asc(ModerationQueueItemDto.Fields.type)), 0, 20);
        assertThat(sortedByType.items()).hasSize(2);

        feedbackAccessService.confirmFeedbackViolation(feedback.id(), actorId);
        FeedbackAccessService.ModerationQueuePage afterConfirm = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.empty(), Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), 0, 20);
        assertThat(afterConfirm.totalCount()).isEqualTo(1);

        assertThat(feedbackAccessService.findConfirmedViolationFeedback(PageRequest.of(0, 10))).extracting(FeedbackDto::id).contains(feedback.id());

        FeedbackAccessService.ModerationQueuePage withConfirmed = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.builder().statuses(Set.of(FeedbackModerationStatus.CONFIRMED_HIDDEN)).build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), 0, 20);
        assertThat(withConfirmed.totalCount()).isEqualTo(1);

        feedbackAccessService.confirmCommentViolation(comment.id(), actorId);
        assertThat(feedbackAccessService.findConfirmedViolationComments(PageRequest.of(0, 10))).extracting(FeedbackCommentDto::id).contains(comment.id());

        feedbackAccessService.approveFeedback(feedback.id(), actorId);
        feedbackAccessService.approveComment(comment.id(), actorId);
        FeedbackAccessService.ModerationQueuePage afterApprove = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.empty(), Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), 0, 20);
        assertThat(afterApprove.totalCount()).isEqualTo(0);
    }

    @Test
    void moderationQueue_authorAndReasonAndDateFilters() {
        Long authorId = newUser("VerySpecialAuthorName");
        Long ownerId = newUser("Owner3");
        Long profileId = newProviderProfile(ownerId);
        Long actorId = newUser("Moderator3");
        FeedbackDto feedback = feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, authorId, 1, "Bad", null));
        feedbackAccessService.flagFeedback(feedback.id(), actorId, "VerySpecificReasonText");

        FeedbackAccessService.ModerationQueuePage byAuthor = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.builder().authorName("VerySpecialAuthorName").build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), 0, 20);
        assertThat(byAuthor.totalCount()).isEqualTo(1);

        FeedbackAccessService.ModerationQueuePage byReason = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.builder().reasonText("VerySpecificReason").build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), 0, 20);
        assertThat(byReason.totalCount()).isEqualTo(1);

        FeedbackAccessService.ModerationQueuePage byDateRange = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.builder().createdAtStart(Instant.now().minus(1, ChronoUnit.HOURS)).build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), 0, 20);
        assertThat(byDateRange.totalCount()).isEqualTo(1);

        FeedbackAccessService.ModerationQueuePage byDateRangeExcluding = feedbackAccessService.findModerationQueue(
                ModerationQueueFilterDto.builder().createdAtEnd(Instant.now().minus(1, ChronoUnit.HOURS)).build(),
                Sort.by(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt)), 0, 20);
        assertThat(byDateRangeExcluding.totalCount()).isEqualTo(0);
    }

    @Test
    void rejectFeedback_and_rejectComment_permanentlyRemove() {
        Long authorId = newUser("Author4");
        Long ownerId = newUser("Owner4");
        Long profileId = newProviderProfile(ownerId);
        Long actorId = newModerator("Moderator4");
        FeedbackDto feedback = feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, authorId, 1, "Bad", null));
        FeedbackCommentDto comment = feedbackAccessService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "Reply", null));

        feedbackAccessService.rejectComment(comment.id(), actorId);
        assertThat(feedbackAccessService.findCommentById(comment.id())).isEmpty();

        feedbackAccessService.rejectFeedback(feedback.id(), actorId);
        assertThat(feedbackAccessService.findFeedbackById(feedback.id())).isEmpty();
    }

    private enum GuardedModerationOperation {
        APPROVE_FEEDBACK, APPROVE_COMMENT, CONFIRM_FEEDBACK_VIOLATION, CONFIRM_COMMENT_VIOLATION, REJECT_FEEDBACK, REJECT_COMMENT
    }

    private static Stream<GuardedModerationOperation> guardedModerationOperations() {
        return Stream.of(GuardedModerationOperation.values());
    }

    private void invokeGuardedOperation(GuardedModerationOperation operation, Long feedbackId, Long commentId, Long actorId) {
        switch (operation) {
            case APPROVE_FEEDBACK -> feedbackAccessService.approveFeedback(feedbackId, actorId);
            case APPROVE_COMMENT -> feedbackAccessService.approveComment(commentId, actorId);
            case CONFIRM_FEEDBACK_VIOLATION -> feedbackAccessService.confirmFeedbackViolation(feedbackId, actorId);
            case CONFIRM_COMMENT_VIOLATION -> feedbackAccessService.confirmCommentViolation(commentId, actorId);
            case REJECT_FEEDBACK -> feedbackAccessService.rejectFeedback(feedbackId, actorId);
            case REJECT_COMMENT -> feedbackAccessService.rejectComment(commentId, actorId);
        }
    }

    @ParameterizedTest
    @MethodSource("guardedModerationOperations")
    void guardedModerationMethod_actorNotPrivileged_throwsAccessDeniedException_stateUnchanged(GuardedModerationOperation operation) {
        Long authorId = newUser("Author5");
        Long ownerId = newUser("Owner5");
        Long profileId = newProviderProfile(ownerId);
        Long nonPrivilegedActorId = newUser("NonPrivileged");
        FeedbackDto feedback = feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, authorId, 1, "Bad", null));
        FeedbackCommentDto comment = feedbackAccessService.saveComment(new FeedbackCommentSaveDto(null, feedback.id(), ownerId, "Reply", null));
        feedbackAccessService.flagFeedback(feedback.id(), nonPrivilegedActorId, "Spam");
        feedbackAccessService.flagComment(comment.id(), nonPrivilegedActorId, "Rude");

        FeedbackModerationStatus feedbackStatusBefore = feedbackAccessService.findFeedbackById(feedback.id()).orElseThrow().moderationStatus();
        FeedbackModerationStatus commentStatusBefore = feedbackAccessService.findCommentById(comment.id()).orElseThrow().moderationStatus();

        assertThatThrownBy(() -> invokeGuardedOperation(operation, feedback.id(), comment.id(), nonPrivilegedActorId))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(feedbackAccessService.findFeedbackById(feedback.id())).isPresent();
        assertThat(feedbackAccessService.findFeedbackById(feedback.id()).orElseThrow().moderationStatus()).isEqualTo(feedbackStatusBefore);
        assertThat(feedbackAccessService.findCommentById(comment.id())).isPresent();
        assertThat(feedbackAccessService.findCommentById(comment.id()).orElseThrow().moderationStatus()).isEqualTo(commentStatusBefore);
    }

    @Test
    void flagFeedback_byPlainUser_succeeds() {
        Long authorId = newUser("Author6");
        Long ownerId = newUser("Owner6");
        Long profileId = newProviderProfile(ownerId);
        Long plainUserId = newUser("PlainFlagger");
        FeedbackDto feedback = feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, authorId, 1, "Bad", null));

        feedbackAccessService.flagFeedback(feedback.id(), plainUserId, "Spam");

        assertThat(feedbackAccessService.findFeedbackById(feedback.id())).isPresent();
        assertThat(feedbackAccessService.findFeedbackById(feedback.id()).orElseThrow().moderationStatus()).isEqualTo(FeedbackModerationStatus.HIDDEN);
    }

    @Test
    void approveFeedback_auditCaptureThrows_rollsBackModerationStatus() {
        Long authorId = newUser("Author7");
        Long ownerId = newUser("Owner7");
        Long profileId = newProviderProfile(ownerId);
        Long actorId = newModerator("Moderator7");
        FeedbackDto feedback = feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, authorId, 1, "Bad", null));
        feedbackAccessService.flagFeedback(feedback.id(), actorId, "Spam");

        doThrow(new RuntimeException("audit capture failed")).when(auditPort).captureUpdate(any(), any(), any());

        assertThatThrownBy(() -> feedbackAccessService.approveFeedback(feedback.id(), actorId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("audit capture failed");

        FeedbackDto reread = feedbackAccessService.findFeedbackById(feedback.id()).orElseThrow();
        assertThat(reread.moderationStatus()).isEqualTo(FeedbackModerationStatus.HIDDEN);
    }

    @Test
    void rejectFeedback_auditCaptureThrows_rollsBackDeletion() {
        Long authorId = newUser("Author8");
        Long ownerId = newUser("Owner8");
        Long profileId = newProviderProfile(ownerId);
        Long actorId = newModerator("Moderator8");
        FeedbackDto feedback = feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, authorId, 1, "Bad", null));

        doThrow(new RuntimeException("audit capture failed")).when(auditPort).captureDeletion(any(), any(), any());

        assertThatThrownBy(() -> feedbackAccessService.rejectFeedback(feedback.id(), actorId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("audit capture failed");

        assertThat(feedbackAccessService.findFeedbackById(feedback.id())).isPresent();
    }
}
