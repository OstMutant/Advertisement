package org.ost.integrationtests.level1.feedback;

import org.junit.jupiter.api.Test;
import org.ost.platform.feedback.dto.FeedbackCommentSnapshotDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.ChangeEntry.FieldChange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context, no DB) — {@link FeedbackCommentSnapshotDto#diff} is pure
 * field comparison with zero side effects, the same kind of logic {@code integration-tests} hosts
 * even though it never touches a database.
 */
class FeedbackCommentSnapshotDtoTest {

    @Test
    void diff_noPrevious_returnsChangesForAllSetFields() {
        FeedbackCommentSnapshotDto current = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, "Rude");

        List<ChangeEntry> changes = current.diff(null);

        assertThat(changes).containsExactlyInAnyOrder(
                new FieldChange(FeedbackCommentSnapshotDto.Fields.moderationStatus, "", "HIDDEN"),
                new FieldChange(FeedbackCommentSnapshotDto.Fields.moderationReason, null, "Rude"));
    }

    @Test
    void diff_identicalSnapshots_returnsNoChanges() {
        FeedbackCommentSnapshotDto previous = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, "Rude");
        FeedbackCommentSnapshotDto current = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, "Rude");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).isEmpty();
    }

    @Test
    void diff_moderationStatusChanged_returnsSingleFieldChange() {
        FeedbackCommentSnapshotDto previous = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, "Rude");
        FeedbackCommentSnapshotDto current = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.CONFIRMED_HIDDEN, "Rude");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(FeedbackCommentSnapshotDto.Fields.moderationStatus, "HIDDEN", "CONFIRMED_HIDDEN"));
    }

    @Test
    void diff_moderationReasonChanged_returnsSingleFieldChange() {
        FeedbackCommentSnapshotDto previous = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, "Old reason");
        FeedbackCommentSnapshotDto current = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, "New reason");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(FeedbackCommentSnapshotDto.Fields.moderationReason, "Old reason", "New reason"));
    }

    @Test
    void diff_wrongPreviousType_treatsAsNoPrevious() {
        FeedbackCommentSnapshotDto current = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, "Rude");
        org.ost.platform.audit.api.AuditableSnapshot other = new org.ost.platform.feedback.dto.FeedbackSnapshotDto(FeedbackModerationStatus.NEW, null);

        List<ChangeEntry> changes = current.diff(other);

        assertThat(changes).containsExactlyInAnyOrder(
                new FieldChange(FeedbackCommentSnapshotDto.Fields.moderationStatus, "", "HIDDEN"),
                new FieldChange(FeedbackCommentSnapshotDto.Fields.moderationReason, null, "Rude"));
    }

    @Test
    void entityType_isFeedbackComment() {
        FeedbackCommentSnapshotDto dto = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.NEW, null);
        assertThat(dto.entityType()).isEqualTo(org.ost.platform.core.model.EntityType.FEEDBACK_COMMENT);
    }

    @Test
    void displayName_isEmpty() {
        FeedbackCommentSnapshotDto dto = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.NEW, null);
        assertThat(dto.displayName()).isEmpty();
    }

    @Test
    void allFields_returnsBothFieldsWithNullFrom() {
        FeedbackCommentSnapshotDto dto = new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, "Rude");

        assertThat(dto.allFields()).containsExactly(
                new FieldChange(FeedbackCommentSnapshotDto.Fields.moderationStatus, null, "HIDDEN"),
                new FieldChange(FeedbackCommentSnapshotDto.Fields.moderationReason, null, "Rude"));
    }
}
