package org.ost.advertisement.events.spi;

import org.ost.advertisement.events.dto.ActivityItemDto;

import java.util.List;

public interface UserActivityExtension {

    List<ActivityItemDto> getPhotoActivity(Long userId);
}
