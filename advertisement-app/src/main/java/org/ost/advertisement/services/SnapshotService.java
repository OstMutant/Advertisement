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
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.model.ChangeEntry.NoteEntry;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.ost.advertisement.repository.advertisement.AdvertisementHistoryProjection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SnapshotService {

    public record SnapshotContent(String title, String description, int version) {}
    public record UserSnapshotState(Long userId, String name, Role role) {}

    private static final String ENTITY_ADVERTISEMENT = "ADVERTISEMENT";
    private static final String ENTITY_USER          = "USER";
    private static final String ENTITY_USER_SETTINGS = "USER_SETTINGS";

    private final NamedParameterJdbcTemplate    jdbc;
    @Qualifier("userSettingsObjectMapper") private final ObjectMapper objectMapper;
    private final AuditDiffEngine               diffEngine;
    private final AdvertisementHistoryProjection historyProjection;
    private final ObjectProvider<AdvertisementHistoryExtension> historyExtension;

    // ── Capture ───────────────────────────────────────────────────────────────

    @Transactional
    public void captureAdvertisement(Advertisement ad, ActionType actionType, Long changedByUserId) {
        AdvertisementSnapshot current = AdvertisementSnapshot.from(ad);
        List<ChangeEntry> changes = switch (actionType) {
            case CREATED -> diffEngine.diffFromNull(current);
            case UPDATED -> {
                AdvertisementSnapshot prev = loadLastSnapshot(ENTITY_ADVERTISEMENT, ad.getId(), AdvertisementSnapshot.class);
                yield prev != null ? diffEngine.diff(prev, current) : diffEngine.diffFromNull(current);
            }
            case DELETED -> null;
        };
        insertAuditLog(ENTITY_ADVERTISEMENT, ad.getId(), actionType, current, changes, changedByUserId);
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
                        : loadLastSnapshot(ENTITY_USER, user.getId(), UserSnapshot.class);
                yield prev != null ? diffEngine.diff(prev, current) : diffEngine.diffFromNull(current);
            }
            case DELETED -> null;
        };
        insertAuditLog(ENTITY_USER, user.getId(), actionType, current, changes, changedByUserId);
    }

    @Transactional
    public void captureSettingsChange(User user, UserSettings oldSettings, UserSettings newSettings, Long changedByUserId) {
        SettingsSnapshot prev    = SettingsSnapshot.from(oldSettings);
        SettingsSnapshot current = SettingsSnapshot.from(newSettings);
        List<ChangeEntry> changes = diffEngine.diff(prev, current);
        if (changes.isEmpty()) return;
        insertAuditLog(ENTITY_USER_SETTINGS, user.getId(), ActionType.UPDATED, current, changes, changedByUserId);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public Optional<UserSettings> getSettingsFromSnapshot(Long snapshotId) {
        return jdbc.query(
                "SELECT snapshot_data::text FROM audit_log WHERE id = :id AND entity_type = :type",
                new MapSqlParameterSource().addValue("id", snapshotId).addValue("type", ENTITY_USER_SETTINGS),
                (rs, row) -> fromJson(rs.getString(1), UserSettings.class)
        ).stream().findFirst();
    }

    public Optional<UserSnapshotState> getUserStateAt(Long snapshotId) {
        return jdbc.query(
                "SELECT entity_id, snapshot_data->>'name' AS name, snapshot_data->>'role' AS role " +
                "FROM audit_log WHERE id = :id AND entity_type = :type",
                new MapSqlParameterSource().addValue("id", snapshotId).addValue("type", ENTITY_USER),
                (rs, row) -> new UserSnapshotState(
                        rs.getLong("entity_id"),
                        rs.getString("name"),
                        Role.valueOf(rs.getString("role"))
                )
        ).stream().findFirst();
    }

    public Optional<UserSnapshotState> getUserStateBefore(Long snapshotId) {
        return jdbc.query("""
                SELECT prev.entity_id, prev.snapshot_data->>'name' AS name, prev.snapshot_data->>'role' AS role
                FROM audit_log cur
                JOIN LATERAL (
                    SELECT entity_id, snapshot_data
                    FROM audit_log
                    WHERE entity_type = 'USER' AND entity_id = cur.entity_id AND created_at < cur.created_at
                    ORDER BY created_at DESC LIMIT 1
                ) prev ON true
                WHERE cur.id = :snapshotId AND cur.entity_type = 'USER'
                """,
                new MapSqlParameterSource("snapshotId", snapshotId),
                (rs, row) -> new UserSnapshotState(
                        rs.getLong("entity_id"),
                        rs.getString("name"),
                        Role.valueOf(rs.getString("role"))
                )
        ).stream().findFirst();
    }

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId) {
        return jdbc.query("""
                SELECT a.snapshot_data->>'title'       AS title,
                       a.snapshot_data->>'description' AS description,
                       (SELECT COUNT(*) FROM audit_log b
                        WHERE b.entity_type = 'ADVERTISEMENT' AND b.entity_id = a.entity_id
                          AND b.created_at <= a.created_at)::int AS version
                FROM audit_log a
                WHERE a.id = :id AND a.entity_type = 'ADVERTISEMENT'
                """,
                new MapSqlParameterSource("id", snapshotId),
                (rs, row) -> new SnapshotContent(
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("version")
                )
        ).stream().findFirst();
    }

    @Transactional
    public void appendNoteToLastSnapshot(Long advertisementId, String note) {
        Long snapshotId = jdbc.queryForObject(
                "SELECT id FROM audit_log WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId ORDER BY created_at DESC LIMIT 1",
                new MapSqlParameterSource("adId", advertisementId), Long.class);
        if (snapshotId == null) return;

        String currentJson = jdbc.queryForObject(
                "SELECT changes_summary::text FROM audit_log WHERE id = :id",
                new MapSqlParameterSource("id", snapshotId), String.class);

        List<ChangeEntry> entries = new ArrayList<>(fromJsonList(currentJson));
        entries.add(new NoteEntry(note));

        jdbc.update("UPDATE audit_log SET changes_summary = CAST(:s AS JSONB) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", snapshotId).addValue("s", toJson(entries)));
    }

    public List<AdvertisementHistoryDto> getAdvertisementHistory(Long advertisementId, Long currentUserId, boolean showAll) {
        List<AdvertisementHistoryDto> history = historyProjection.queryAll(jdbc,
                new MapSqlParameterSource()
                        .addValue("adId",         advertisementId)
                        .addValue("filterUserId", showAll ? null : currentUserId));
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

    // ── Internals ─────────────────────────────────────────────────────────────

    private void insertAuditLog(String entityType, Long entityId, ActionType actionType,
                                Object snapshotDto, List<ChangeEntry> changes, Long changedByUserId) {
        jdbc.update("""
                INSERT INTO audit_log
                    (entity_type, entity_id, action_type, snapshot_data, changes_summary, changed_by_user_id)
                VALUES (:entityType, :entityId, :actionType,
                        CAST(:snapshotData AS JSONB), CAST(:changes AS JSONB), :changedBy)
                """,
                new MapSqlParameterSource()
                        .addValue("entityType",   entityType)
                        .addValue("entityId",     entityId)
                        .addValue("actionType",   actionType.name())
                        .addValue("snapshotData", toJson(snapshotDto))
                        .addValue("changes",      toChangesJson(changes))
                        .addValue("changedBy",    changedByUserId)
        );
    }

    private <T> T loadLastSnapshot(String entityType, Long entityId, Class<T> type) {
        return jdbc.query(
                "SELECT snapshot_data::text FROM audit_log WHERE entity_type = :type AND entity_id = :id ORDER BY created_at DESC LIMIT 1",
                new MapSqlParameterSource().addValue("type", entityType).addValue("id", entityId),
                (rs, row) -> fromJson(rs.getString(1), type)
        ).stream().findFirst().orElse(null);
    }

    private String toChangesJson(List<ChangeEntry> changes) {
        if (changes == null) return null;
        try {
            ObjectWriter writer = objectMapper.writerFor(CHANGES_TYPE);
            return writer.writeValueAsString(changes);
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
