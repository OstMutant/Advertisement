package org.ost.platform.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.validation.ValidRange;

import java.time.Instant;
import java.util.Set;

@FieldNameConstants
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ValidRange(start = "fromDate", end = "toDate", message = "fromDate must not be after toDate")
public class AuditTimelineFilterDto {

    private Long actorId;
    private Set<EntityType> entityTypes;
    private Set<ActionType> actionTypes;
    private Instant fromDate;
    private Instant toDate;

    public static AuditTimelineFilterDto empty() {
        return new AuditTimelineFilterDto();
    }
}
