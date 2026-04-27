package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.ActivityItemDto;
import org.ost.advertisement.repository.activity.ActivityRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository repository;

    public List<ActivityItemDto> getForUser(Long userId) {
        return repository.findByUserId(userId);
    }
}
