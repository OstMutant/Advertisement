package org.ost.platform.audit.spi;

import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.List;

public interface ActivityItemFieldsProvider {
    boolean supports(EntityType entityType);
    List<ChangeEntry> expandFields(ActivityItemDto item);
}
