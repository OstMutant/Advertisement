package org.ost.user.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.spi.UserSettingsChangedHook;
import org.ost.user.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository                repository;
    private final ComponentFactory<UserSettingsChangedHook> hookFactory;
    private final ComponentFactory<AuditPort>           auditPortFactory;

    public UserSettingsDto load(@NonNull Long userId) {
        return repository.load(userId);
    }

    @Transactional
    public void save(@NonNull Long userId, @NonNull UserSettingsDto settings) {
        UserSettingsDto old = repository.load(userId);
        repository.save(userId, settings);
        hookFactory.ifAvailable(hook -> hook.onSettingsChanged(userId, settings));
        auditPortFactory.ifAvailable(p -> p.captureUpdate(userId,
                SettingsSnapshotDto.from(old),
                SettingsSnapshotDto.from(settings),
                userId));
    }
}
