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
        return repository.loadSettings(userId);
    }

    public void updatePageSizes(Long userId, int adsPageSize, int usersPageSize) {
        repository.updatePageSizes(userId, adsPageSize, usersPageSize);
        UserSettings updated = repository.loadSettings(userId);
        eventPublisher.publishEvent(new SettingsChangedEvent(this, userId, updated));
    }
}
