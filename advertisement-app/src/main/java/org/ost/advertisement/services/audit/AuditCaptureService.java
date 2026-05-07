package org.ost.advertisement.services.audit;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.services.audit.AdvertisementSnapshot;
import org.ost.advertisement.services.audit.AuditDiffEngine;
import org.ost.advertisement.services.audit.SettingsSnapshot;
import org.ost.advertisement.services.audit.UserSnapshot;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.repository.audit.AuditLogDescriptor;
import org.ost.advertisement.repository.audit.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditCaptureService {

    private final AuditLogRepository  auditLogRepository;
    private final AuditDiffEngine     diffEngine;
    private final AuditSnapshotMapper mapper;

    @Transactional
    public void captureAdvertisement(Advertisement ad, ActionType actionType, Long changedByUserId) {
        AdvertisementSnapshot current = AdvertisementSnapshot.from(ad);
        List<ChangeEntry> changes = switch (actionType) {
            case CREATED -> diffEngine.diffFromNull(current);
            case UPDATED -> {
                AdvertisementSnapshot prev = loadLastSnapshot(AuditLogDescriptor.EntityType.ADVERTISEMENT, ad.getId(), AdvertisementSnapshot.class);
                yield prev != null ? diffEngine.diff(prev, current) : diffEngine.diffFromNull(current);
            }
            case DELETED -> null;
        };
        auditLogRepository.insert(AuditLogDescriptor.EntityType.ADVERTISEMENT, ad.getId(),
                actionType.name(), mapper.toJson(current), mapper.toChangesJson(changes), changedByUserId);
    }

    @Transactional
    public void captureUser(User user, ActionType actionType, Long changedByUserId) {
        captureUser(user, null, actionType, changedByUserId);
    }

    @Transactional
    public void captureUser(User user, User before, ActionType actionType, Long changedByUserId) {
        UserSnapshot current = UserSnapshot.from(user);
        List<ChangeEntry> changes = switch (actionType) {
            case CREATED -> diffEngine.diffFromNull(current);
            case UPDATED -> {
                UserSnapshot prev = before != null
                        ? UserSnapshot.from(before)
                        : loadLastSnapshot(AuditLogDescriptor.EntityType.USER, user.getId(), UserSnapshot.class);
                yield prev != null ? diffEngine.diff(prev, current) : diffEngine.diffFromNull(current);
            }
            case DELETED -> null;
        };
        auditLogRepository.insert(AuditLogDescriptor.EntityType.USER, user.getId(),
                actionType.name(), mapper.toJson(current), mapper.toChangesJson(changes), changedByUserId);
    }

    @Transactional
    public void captureSettingsChange(User user, UserSettings oldSettings, UserSettings newSettings, Long changedByUserId) {
        SettingsSnapshot prev    = SettingsSnapshot.from(oldSettings);
        SettingsSnapshot current = SettingsSnapshot.from(newSettings);
        List<ChangeEntry> changes = diffEngine.diff(prev, current);
        if (changes.isEmpty()) return;
        auditLogRepository.insert(AuditLogDescriptor.EntityType.USER_SETTINGS, user.getId(),
                ActionType.UPDATED.name(), mapper.toJson(current), mapper.toChangesJson(changes), changedByUserId);
    }

    @Transactional
    public void captureInitialSettings(User user, UserSettings settings, Long changedByUserId) {
        SettingsSnapshot current = SettingsSnapshot.from(settings);
        List<ChangeEntry> changes = diffEngine.diffFromNull(current);
        if (changes.isEmpty()) return;
        auditLogRepository.insert(
                AuditLogDescriptor.EntityType.USER_SETTINGS,
                user.getId(),
                ActionType.UPDATED.name(),
                mapper.toJson(current),
                mapper.toChangesJson(changes),
                changedByUserId);
    }

    private <T> T loadLastSnapshot(String entityType, Long entityId, Class<T> type) {
        return auditLogRepository.getLastSnapshotData(entityType, entityId)
                .map(json -> mapper.fromJson(json, type))
                .orElse(null);
    }
}
