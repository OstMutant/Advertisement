package org.ost.advertisement.core.spi;

import org.ost.advertisement.audit.dto.ActivityItemDto;

import java.util.List;

public interface UserActivityExtension {

    List<ActivityItemDto> getMediaActivity(Long userId);
}
