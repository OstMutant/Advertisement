package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.events.SettingsChangedEvent;
import org.ost.advertisement.repository.user.UserSettingsRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository    repository;
    private final ApplicationEventPublisher eventPublisher;

    public UserSettings load(Long userId) {
        return repository.load(userId);
    }

    public void save(Long userId, UserSettings settings) {
        repository.save(userId, settings);
        eventPublisher.publishEvent(new SettingsChangedEvent(this, userId, settings));
    }
}
