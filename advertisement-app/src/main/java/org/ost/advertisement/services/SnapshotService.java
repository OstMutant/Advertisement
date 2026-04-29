package org.ost.advertisement.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.springframework.beans.factory.annotation.Qualifier;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.model.ChangeEntry.FieldChange;
import org.ost.advertisement.events.model.ChangeEntry.SettingChange;
import org.ost.advertisement.events.model.ChangeEntry.NoteEntry;
import org.ost.advertisement.repository.advertisement.AdvertisementHistoryProjection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SnapshotService {

    public record SnapshotContent(String title, String description, int version) {}
    public record UserSnapshotState(Long userId, String name, Role role) {}

    private final NamedParameterJdbcTemplate    jdbc;
    @Qualifier("userSettingsObjectMapper") private final ObjectMapper objectMapper;
    private final AdvertisementHistoryProjection historyProjection;
    private final ObjectProvider<AdvertisementHistoryExtension> historyExtension;

    @Transactional
    public void captureAdvertisement(Advertisement ad, ActionType actionType, Long changedByUserId) {
        List<ChangeEntry> changes = switch (actionType) {
            case CREATED -> List.of(
                    new FieldChange("title",       null, truncate(ad.getTitle())),
                    new FieldChange("description", null, truncate(ad.getDescription()))
            );
            case UPDATED -> computeDiff(ad);
            case DELETED -> null;
        };

        jdbc.update("""
                INSERT INTO advertisement_snapshot
                    (advertisement_id, title, description, changed_by_user_id, action_type, version, changes_summary)
                VALUES (:adId, :title, :description, :changedBy, :actionType,
                    COALESCE((SELECT MAX(version) FROM advertisement_snapshot WHERE advertisement_id = :adId), 0) + 1,
                    CAST(:changesSummary AS JSONB))
                """,
                new MapSqlParameterSource()
                        .addValue("adId",           ad.getId())
                        .addValue("title",          ad.getTitle())
                        .addValue("description",    ad.getDescription())
                        .addValue("changedBy",      changedByUserId)
                        .addValue("actionType",     actionType.name())
                        .addValue("changesSummary", toChangesJson(changes))
        );
    }

    @Transactional
    public void captureUser(User user, ActionType actionType, Long changedByUserId) {
        captureUser(user, null, actionType, changedByUserId);
    }

    @Transactional
    public void captureUser(User user, User before, ActionType actionType, Long changedByUserId) {
        List<ChangeEntry> changes = switch (actionType) {
            case CREATED -> List.of(
                    new FieldChange("name",  null, truncate(user.getName())),
                    new FieldChange("email", null, truncate(user.getEmail())),
                    new FieldChange("role",  null, user.getRole().name())
            );
            case UPDATED -> computeUserDiff(user, before);
            case DELETED -> null;
        };
        insertUserSnapshot(user, null, actionType, changedByUserId, changes);
    }

    @Transactional
    public void captureSettingsChange(User user, UserSettings oldSettings, UserSettings newSettings, Long changedByUserId) {
        List<ChangeEntry> parts = new ArrayList<>();
        if (oldSettings.getAdsPageSize() != newSettings.getAdsPageSize()) {
            parts.add(new SettingChange("adsPageSize", oldSettings.getAdsPageSize(), newSettings.getAdsPageSize()));
        }
        if (oldSettings.getUsersPageSize() != newSettings.getUsersPageSize()) {
            parts.add(new SettingChange("usersPageSize", oldSettings.getUsersPageSize(), newSettings.getUsersPageSize()));
        }
        if (parts.isEmpty()) return;
        insertUserSnapshot(user, toJson(newSettings), ActionType.UPDATED, changedByUserId, parts);
    }

    private void insertUserSnapshot(User user, String settingsJson, ActionType actionType,
                                    Long changedByUserId, List<ChangeEntry> changes) {
        jdbc.update("""
                INSERT INTO user_snapshot
                    (user_id, name, email, role, settings, changed_by_user_id, action_type, version, changes_summary)
                VALUES (:userId, :name, :email, :role, CAST(:settings AS JSONB), :changedBy, :actionType,
                    COALESCE((SELECT MAX(version) FROM user_snapshot WHERE user_id = :userId), 0) + 1,
                    CAST(:changesSummary AS JSONB))
                """,
                new MapSqlParameterSource()
                        .addValue("userId",         user.getId())
                        .addValue("name",           user.getName())
                        .addValue("email",          user.getEmail())
                        .addValue("role",           user.getRole().name())
                        .addValue("settings",       settingsJson)
                        .addValue("changedBy",      changedByUserId)
                        .addValue("actionType",     actionType.name())
                        .addValue("changesSummary", toChangesJson(changes))
        );
    }

    public Optional<UserSettings> getSettingsFromSnapshot(Long snapshotId) {
        return jdbc.query(
                "SELECT settings FROM user_snapshot WHERE id = :id AND settings IS NOT NULL",
                new MapSqlParameterSource("id", snapshotId),
                (rs, row) -> fromJson(rs.getString("settings"), UserSettings.class)
        ).stream().findFirst();
    }

    public Optional<UserSnapshotState> getUserStateAt(Long snapshotId) {
        return jdbc.query(
                "SELECT user_id, name, role FROM user_snapshot WHERE id = :id",
                new MapSqlParameterSource("id", snapshotId),
                (rs, row) -> new UserSnapshotState(
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        Role.valueOf(rs.getString("role"))
                )
        ).stream().findFirst();
    }

    public Optional<UserSnapshotState> getUserStateBefore(Long snapshotId) {
        return jdbc.query("""
                SELECT prev.user_id, prev.name, prev.role
                FROM user_snapshot cur
                JOIN user_snapshot prev
                     ON prev.user_id = cur.user_id AND prev.version = cur.version - 1
                WHERE cur.id = :snapshotId
                """,
                new MapSqlParameterSource("snapshotId", snapshotId),
                (rs, row) -> new UserSnapshotState(
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        Role.valueOf(rs.getString("role"))
                )
        ).stream().findFirst();
    }

    private List<ChangeEntry> computeUserDiff(User user, User before) {
        if (before != null) {
            return buildUserFieldDiffs(user.getName(), user.getEmail(), user.getRole().name(),
                    before.getName(), before.getEmail(), before.getRole().name());
        }
        List<List<ChangeEntry>> results = jdbc.query("""
                SELECT name, email, role FROM user_snapshot
                WHERE user_id = :userId
                ORDER BY version DESC LIMIT 1
                """,
                new MapSqlParameterSource("userId", user.getId()),
                (rs, row) -> buildUserFieldDiffs(
                        user.getName(), user.getEmail(), user.getRole().name(),
                        rs.getString("name"), rs.getString("email"), rs.getString("role")
                ));
        return results.isEmpty() ? null : results.get(0);
    }

    private static List<ChangeEntry> buildUserFieldDiffs(String newName, String newEmail, String newRole,
                                                          String prevName, String prevEmail, String prevRole) {
        List<ChangeEntry> parts = new ArrayList<>();
        if (!Objects.equals(newName, prevName)) {
            parts.add(new FieldChange("name", truncate(prevName), truncate(newName)));
        }
        if (!Objects.equals(newEmail, prevEmail)) {
            parts.add(new FieldChange("email", truncate(prevEmail), truncate(newEmail)));
        }
        if (!Objects.equals(newRole, prevRole)) {
            parts.add(new FieldChange("role", prevRole, newRole));
        }
        return parts.isEmpty() ? null : parts;
    }

    @Transactional
    public void appendNoteToLastSnapshot(Long advertisementId, String note) {
        Long snapshotId = jdbc.queryForObject(
                "SELECT id FROM advertisement_snapshot WHERE advertisement_id = :adId ORDER BY version DESC LIMIT 1",
                new MapSqlParameterSource("adId", advertisementId), Long.class);
        if (snapshotId == null) return;

        String currentJson = jdbc.queryForObject(
                "SELECT changes_summary::text FROM advertisement_snapshot WHERE id = :id",
                new MapSqlParameterSource("id", snapshotId), String.class);

        List<ChangeEntry> entries = new ArrayList<>(fromJsonList(currentJson));
        entries.add(new NoteEntry(note));

        jdbc.update("UPDATE advertisement_snapshot SET changes_summary = CAST(:s AS JSONB) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", snapshotId).addValue("s", toChangesJson(entries)));
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

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId) {
        return jdbc.query(
                "SELECT title, description, version FROM advertisement_snapshot WHERE id = :id",
                new MapSqlParameterSource("id", snapshotId),
                (rs, row) -> new SnapshotContent(
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("version")
                )
        ).stream().findFirst();
    }

    private List<ChangeEntry> computeDiff(Advertisement ad) {
        List<List<ChangeEntry>> results = jdbc.query("""
                SELECT title, description FROM advertisement_snapshot
                WHERE advertisement_id = :adId
                ORDER BY version DESC LIMIT 1
                """,
                new MapSqlParameterSource("adId", ad.getId()),
                (rs, row) -> {
                    List<ChangeEntry> parts = new ArrayList<>();
                    String prevTitle = rs.getString("title");
                    String prevDesc  = rs.getString("description");
                    if (!Objects.equals(ad.getTitle(), prevTitle)) {
                        parts.add(new FieldChange("title", truncate(prevTitle), truncate(ad.getTitle())));
                    }
                    if (!Objects.equals(ad.getDescription(), prevDesc)) {
                        parts.add(new FieldChange("description", truncate(prevDesc), truncate(ad.getDescription())));
                    }
                    return parts.isEmpty() ? null : parts;
                });
        return results.isEmpty() ? null : results.get(0);
    }

    private static final TypeReference<List<ChangeEntry>> CHANGES_TYPE = new TypeReference<>() {};

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
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    List<ChangeEntry> fromJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
