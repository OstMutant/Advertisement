package org.ost.integrationtests.level1.feedback;

import org.junit.jupiter.api.Test;
import org.ost.platform.feedback.dto.FeedbackSnapshotDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.ChangeEntry.FieldChange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context, no DB) — {@link FeedbackSnapshotDto#diff} is pure field
 * comparison with zero side effects, the same kind of logic {@code integration-tests} hosts even
 * though it never touches a database.
 */
class FeedbackSnapshotDtoTest {

    @Test
    void diff_noPrevious_returnsChangesForAllSetFields() {
        FeedbackSnapshotDto current = new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, "Spam");

        List<ChangeEntry> changes = current.diff(null);

        assertThat(changes).containsExactlyInAnyOrder(
                new FieldChange(FeedbackSnapshotDto.Fields.moderationStatus, "", "HIDDEN"),
                new FieldChange(FeedbackSnapshotDto.Fields.moderationReason, null, "Spam"));
    }

    @Test
    void diff_identicalSnapshots_returnsNoChanges() {
        FeedbackSnapshotDto previous = new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, "Spam");
        FeedbackSnapshotDto current = new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, "Spam");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).isEmpty();
    }

    @Test
    void diff_moderationStatusChanged_returnsSingleFieldChange() {
        FeedbackSnapshotDto previous = new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, "Spam");
        FeedbackSnapshotDto current = new FeedbackSnapshotDto(FeedbackModerationStatus.CONFIRMED_HIDDEN, "Spam");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(FeedbackSnapshotDto.Fields.moderationStatus, "HIDDEN", "CONFIRMED_HIDDEN"));
    }

    @Test
    void diff_moderationReasonChanged_returnsSingleFieldChange() {
        FeedbackSnapshotDto previous = new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, "Old reason");
        FeedbackSnapshotDto current = new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, "New reason");

        List<ChangeEntry> changes = current.diff(previous);

        assertThat(changes).containsExactly(
                new FieldChange(FeedbackSnapshotDto.Fields.moderationReason, "Old reason", "New reason"));
    }

    @Test
    void diff_wrongPreviousType_treatsAsNoPrevious() {
        FeedbackSnapshotDto current = new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, "Spam");
        org.ost.platform.audit.api.AuditableSnapshot other = new org.ost.platform.feedback.dto.FeedbackCommentSnapshotDto(FeedbackModerationStatus.NEW, null);

        List<ChangeEntry> changes = current.diff(other);

        assertThat(changes).containsExactlyInAnyOrder(
                new FieldChange(FeedbackSnapshotDto.Fields.moderationStatus, "", "HIDDEN"),
                new FieldChange(FeedbackSnapshotDto.Fields.moderationReason, null, "Spam"));
    }

    @Test
    void entityType_isFeedback() {
        FeedbackSnapshotDto dto = new FeedbackSnapshotDto(FeedbackModerationStatus.NEW, null);
        assertThat(dto.entityType()).isEqualTo(org.ost.platform.core.model.EntityType.FEEDBACK);
    }

    @Test
    void displayName_isEmpty() {
        FeedbackSnapshotDto dto = new FeedbackSnapshotDto(FeedbackModerationStatus.NEW, null);
        assertThat(dto.displayName()).isEmpty();
    }

    @Test
    void allFields_returnsBothFieldsWithNullFrom() {
        FeedbackSnapshotDto dto = new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, "Spam");

        assertThat(dto.allFields()).containsExactly(
                new FieldChange(FeedbackSnapshotDto.Fields.moderationStatus, null, "HIDDEN"),
                new FieldChange(FeedbackSnapshotDto.Fields.moderationReason, null, "Spam"));
    }
}
