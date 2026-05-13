package org.ost.advertisement.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.repository.ActivityRepository;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.spi.UserActivityExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository                    repository;
    private final ObjectProvider<UserActivityExtension> activityExtension;

    public List<ActivityItemDto> getForUser(Long userId) {
        List<ActivityItemDto> base = repository.findByUserId(userId);
        UserActivityExtension ext = activityExtension.getIfAvailable();
        if (ext == null) return base;
        List<ActivityItemDto> combined = new ArrayList<>(base);
        combined.addAll(ext.getPhotoActivity(userId));
        combined.sort(Comparator.comparing(ActivityItemDto::createdAt).reversed());
        return combined.size() > 20 ? combined.subList(0, 20) : combined;
    }
}
