package org.ost.advertisement.core.spi;

import org.ost.advertisement.audit.dto.ActivityItemDto;
import org.ost.advertisement.core.model.ChangeEntry;
import org.ost.advertisement.core.model.EntityType;

import java.util.List;

public interface ActivityItemFieldsProvider {
    boolean supports(EntityType entityType);
    List<ChangeEntry> expandFields(ActivityItemDto item);
}
