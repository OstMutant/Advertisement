package org.ost.platform.core.spi;

import org.ost.platform.audit.dto.ActivityItemDto;

import java.util.List;

public interface UserActivityExtension {

    List<ActivityItemDto> merge(Long userId, List<ActivityItemDto> baseItems);
}
