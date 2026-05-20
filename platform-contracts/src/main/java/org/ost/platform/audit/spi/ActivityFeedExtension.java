package org.ost.platform.audit.spi;

import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.EntityType;

import java.util.List;

public interface ActivityFeedExtension {

    List<ActivityItemDto> merge(EntityType subjectType, Long subjectId, List<ActivityItemDto> baseItems);
}
