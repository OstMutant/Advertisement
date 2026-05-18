package org.ost.platform.audit.spi;

import org.ost.platform.audit.dto.ActivityItemDto;

import java.util.List;

public interface UserActivityExtension {

    List<ActivityItemDto> merge(Long userId, List<ActivityItemDto> baseItems);
}
