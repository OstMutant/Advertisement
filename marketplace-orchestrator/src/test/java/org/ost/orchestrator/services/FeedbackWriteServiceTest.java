package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link FeedbackWriteService}: the caller is the author, only the author edits, and the reviewed profile must exist. */
@ExtendWith(MockitoExtension.class)
class FeedbackWriteServiceTest {

    private static final Long AUTHOR_ID = 10L;
    private static final Long OTHER_USER_ID = 11L;
    private static final Long PROFILE_OWNER_ID = 40L;
    private static final Long PROFILE_ID = 7L;
    private static final Long FEEDBACK_ID = 99L;

    @Mock private FeedbackAccessService feedbackAccessService;
    @Mock private ProviderProfileReadService providerProfileReadService;

    private FeedbackWriteService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackWriteService(feedbackAccessService, providerProfileReadService);
    }

    @Test
    void create_savesWithCallerAsAuthorAndNoIdOrVersion() {
        when(providerProfileReadService.findById(PROFILE_ID))
                .thenReturn(Optional.of(ProviderProfileDto.builder().id(PROFILE_ID).actorId(PROFILE_OWNER_ID).build()));

        service.create(AUTHOR_ID, PROFILE_ID, 5, "Great");

        verify(feedbackAccessService).save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, PROFILE_ID, AUTHOR_ID, 5, "Great", null));
    }

    @Test
    void create_unknownProfile_throwsNotFoundAndNeverSaves() {
        when(providerProfileReadService.findById(PROFILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(AUTHOR_ID, PROFILE_ID, 5, "Great"))
                .isInstanceOf(NoSuchElementException.class);
        verify(feedbackAccessService, never()).save(any());
    }

    @Test
    void edit_byAuthor_savesExistingEntityWithVersion() {
        when(feedbackAccessService.findFeedbackById(FEEDBACK_ID)).thenReturn(Optional.of(feedback(AUTHOR_ID)));

        service.edit(AUTHOR_ID, FEEDBACK_ID, 3, "Changed", 4L);

        verify(feedbackAccessService).save(new FeedbackSaveDto(FEEDBACK_ID, EntityType.PROVIDER_PROFILE, PROFILE_ID, AUTHOR_ID, 3, "Changed", 4L));
    }

    @Test
    void edit_byAnotherUser_throwsAccessDeniedAndNeverSaves() {
        when(feedbackAccessService.findFeedbackById(FEEDBACK_ID)).thenReturn(Optional.of(feedback(AUTHOR_ID)));

        assertThatThrownBy(() -> service.edit(OTHER_USER_ID, FEEDBACK_ID, 3, "Changed", 4L))
                .isInstanceOf(AccessDeniedException.class);
        verify(feedbackAccessService, never()).save(any());
    }

    @Test
    void edit_unknownFeedback_throwsNotFoundAndNeverSaves() {
        when(feedbackAccessService.findFeedbackById(FEEDBACK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(AUTHOR_ID, FEEDBACK_ID, 3, "Changed", 4L))
                .isInstanceOf(NoSuchElementException.class);
        verify(feedbackAccessService, never()).save(any());
    }

    private static FeedbackDto feedback(Long authorId) {
        return new FeedbackDto(FEEDBACK_ID, EntityType.PROVIDER_PROFILE, PROFILE_ID, authorId, 4, "Old",
                FeedbackModerationStatus.NEW, null, Instant.now(), null, 4L);
    }
}
