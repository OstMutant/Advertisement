package org.ost.advertisement.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.AdvertisementSnapshot;
import org.ost.advertisement.audit.AuditDiffEngine;
import org.ost.advertisement.audit.SettingsSnapshot;
import org.ost.advertisement.audit.UserSnapshot;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.model.ChangeEntry.NoteEntry;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.ost.advertisement.repository.audit.AuditLogDescriptor;
import org.ost.advertisement.repository.audit.AuditLogRepository;
import org.ost.advertisement.repository.audit.AuditLogRepository.SnapshotContent;
import org.ost.advertisement.repository.audit.AuditLogRepository.UserSnapshotState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository                             auditLogRepository;
    @Qualifier("userSettingsObjectMapper") private final ObjectMapper objectMapper;
    private final AuditDiffEngine                                diffEngine;
    private final ObjectProvider<AdvertisementHistoryExtension>  historyExtension;

    // ── Capture ───────────────────────────────────────────────────────────────

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
                actionType.name(), toJson(current), toChangesJson(changes), changedByUserId);
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
                actionType.name(), toJson(current), toChangesJson(changes), changedByUserId);
    }

    @Transactional
    public void captureSettingsChange(User user, UserSettings oldSettings, UserSettings newSettings, Long changedByUserId) {
        SettingsSnapshot prev    = SettingsSnapshot.from(oldSettings);
        SettingsSnapshot current = SettingsSnapshot.from(newSettings);
        List<ChangeEntry> changes = diffEngine.diff(prev, current);
        if (changes.isEmpty()) return;
        auditLogRepository.insert(AuditLogDescriptor.EntityType.USER_SETTINGS, user.getId(),
                ActionType.UPDATED.name(), toJson(current), toChangesJson(changes), changedByUserId);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public Optional<UserSettings> getSettingsFromSnapshot(Long snapshotId) {
        return auditLogRepository.getSnapshotData(snapshotId, AuditLogDescriptor.EntityType.USER_SETTINGS)
                .map(json -> fromJson(json, UserSettings.class));
    }

    public Optional<UserSnapshotState> getUserStateAt(Long snapshotId) {
        return auditLogRepository.getUserStateAt(snapshotId);
    }

    public Optional<UserSnapshotState> getUserStateBefore(Long snapshotId) {
        return auditLogRepository.getUserStateBefore(snapshotId);
    }

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId) {
        return auditLogRepository.getSnapshotContent(snapshotId);
    }

    @Transactional
    public void appendNoteToLastSnapshot(Long advertisementId, String note) {
        Long snapshotId = auditLogRepository.findLastSnapshotId(advertisementId).orElse(null);
        if (snapshotId == null) return;
        String currentJson = auditLogRepository.getChangesSummary(snapshotId);
        List<ChangeEntry> entries = new ArrayList<>(fromJsonList(currentJson));
        entries.add(new NoteEntry(note));
        auditLogRepository.updateChangesSummary(snapshotId, toJson(entries));
    }

    public List<AdvertisementHistoryDto> getAdvertisementHistory(Long advertisementId, Long currentUserId, boolean showAll) {
        List<AdvertisementHistoryDto> history = auditLogRepository.getAdvertisementHistory(
                advertisementId, showAll ? null : currentUserId);
        AdvertisementHistoryExtension ext = historyExtension.getIfAvailable();
        if (ext == null) return history;
        return history.stream()
                .map(h -> {
                    List<ChangeEntry> photoChanges = ext.getPhotoChanges(advertisementId, h.version());
                    if (photoChanges.isEmpty()) return h;
                    List<ChangeEntry> combined = new ArrayList<>(photoChanges);
                    combined.addAll(h.changes());
                    return new AdvertisementHistoryDto(h.snapshotId(), h.version(), h.actionType(),
                            h.changedByUserName(), h.createdAt(), h.title(), h.description(),
                            combined, h.prevSnapshotId(), h.prevTitle(), h.prevDescription());
                })
                .toList();
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private static final TypeReference<List<ChangeEntry>> CHANGES_TYPE = new TypeReference<>() {};

    List<ChangeEntry> fromJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, CHANGES_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }

    private <T> T loadLastSnapshot(String entityType, Long entityId, Class<T> type) {
        return auditLogRepository.getLastSnapshotData(entityType, entityId)
                .map(json -> fromJson(json, type))
                .orElse(null);
    }

    private String toChangesJson(List<ChangeEntry> changes) {
        if (changes == null) return null;
        try {
            return objectMapper.writerFor(CHANGES_TYPE).writeValueAsString(changes);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }
}
