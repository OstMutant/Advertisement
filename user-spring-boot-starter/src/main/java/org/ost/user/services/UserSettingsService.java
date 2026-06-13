package org.ost.user.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.dto.UserSettings;
import org.ost.user.dto.audit.SettingsSnapshotDto;
import org.ost.user.events.UserSettingsChangedEvent;
import org.ost.user.repository.UserSettingsRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository      repository;
    private final ApplicationEventPublisher   eventPublisher;
    private final ComponentFactory<AuditPort> auditPortFactory;

    public UserSettings load(@NonNull Long userId) {
        return repository.load(userId);
    }

    @Transactional
    public void save(@NonNull Long userId, @NonNull UserSettings settings) {
        UserSettings old = repository.load(userId);
        repository.save(userId, settings);
        eventPublisher.publishEvent(new UserSettingsChangedEvent(this, userId, settings));
        auditPortFactory.ifAvailable(p -> p.captureUpdate(userId,
                SettingsSnapshotDto.from(old),
                SettingsSnapshotDto.from(settings),
                userId));
    }
}
