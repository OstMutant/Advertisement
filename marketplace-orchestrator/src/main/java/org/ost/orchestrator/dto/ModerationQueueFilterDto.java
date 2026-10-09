package org.ost.orchestrator.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.validation.ValidRange;
import org.ost.platform.feedback.model.FeedbackModerationStatus;

import java.time.Instant;
import java.util.Set;

@FieldNameConstants
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ValidRange(start = "createdAtStart", end = "createdAtEnd", message = "createdAtStart must not be after createdAtEnd")
public class ModerationQueueFilterDto {

    private Set<ModerationQueueItemType> types;

    private Instant createdAtStart;
    private Instant createdAtEnd;

    @Size(max = 255, message = "Author name must not exceed 255 characters")
    private String authorName;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reasonText;

    /** Null or empty means: default to showing only HIDDEN (pending-decision) items -- see FeedbackAccessService.matchesFilter(). */
    private Set<FeedbackModerationStatus> statuses;

    public static ModerationQueueFilterDto empty() {
        return new ModerationQueueFilterDto();
    }
}
