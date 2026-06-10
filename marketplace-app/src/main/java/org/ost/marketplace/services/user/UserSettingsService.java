package org.ost.marketplace.services.user;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.dto.audit.SettingsSnapshotDto;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.events.SettingsChangedEvent;
import org.ost.marketplace.repository.user.UserSettingsRepository;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
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
        eventPublisher.publishEvent(new SettingsChangedEvent(this, userId, settings));
        auditPortFactory.ifAvailable(p -> p.captureUpdate(userId,
                SettingsSnapshotDto.from(old),
                SettingsSnapshotDto.from(settings),
                userId));
    }
}
