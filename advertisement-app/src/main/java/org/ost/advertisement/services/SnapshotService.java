package org.ost.advertisement.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementHistoryDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.entities.ActionType;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.model.ChangeEntry;
import org.ost.advertisement.model.ChangeEntry.FieldChange;
import org.ost.advertisement.model.ChangeEntry.PhotoChange;
import org.ost.advertisement.model.ChangeEntry.SettingChange;
import org.ost.advertisement.model.ChangeEntry.NoteEntry;
import org.ost.advertisement.repository.advertisement.AdvertisementHistoryProjection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SnapshotService {

    public record SnapshotContent(String title, String description, String[] attachmentUrls) {}
    public record UserSnapshotState(Long userId, String name, Role role) {}

    private final NamedParameterJdbcTemplate    jdbc;
    @Qualifier("userSettingsObjectMapper") private final ObjectMapper objectMapper;
    private final AdvertisementHistoryProjection historyProjection;

    @Transactional
    public void captureAdvertisement(Advertisement ad, ActionType actionType, Long changedByUserId) {
        List<String> currentUrls = getActiveAttachmentUrls(ad.getId());
        List<ChangeEntry> changes = switch (actionType) {
            case CREATED -> List.of(
                    new FieldChange("title",       null, truncate(ad.getTitle())),
                    new FieldChange("description", null, truncate(ad.getDescription()))
            );
            case UPDATED -> computeDiff(ad, currentUrls);
            case DELETED -> null;
        };

        Array urlArray = toSqlArray(currentUrls);

        jdbc.update("""
                INSERT INTO advertisement_snapshot
                    (advertisement_id, title, description, attachment_urls, changed_by_user_id, action_type, version, changes_summary)
                VALUES (:adId, :title, :description, :attachmentUrls, :changedBy, :actionType,
                    COALESCE((SELECT MAX(version) FROM advertisement_snapshot WHERE advertisement_id = :adId), 0) + 1,
                    CAST(:changesSummary AS JSONB))
                """,
                new MapSqlParameterSource()
                        .addValue("adId",           ad.getId())
                        .addValue("title",          ad.getTitle())
                        .addValue("description",    ad.getDescription())
                        .addValue("attachmentUrls", urlArray)
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
        insertUserSnapshot(user, toJson(oldSettings), ActionType.UPDATED, changedByUserId, parts);
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
    public void captureAdvertisementAttachmentChange(Long advertisementId, Long changedByUserId) {
        List<String> currentUrls = getActiveAttachmentUrls(advertisementId);
        Array urlArray = toSqlArray(currentUrls);

        record RecentSnapshot(Long id, String changesSummaryJson, String[] baselineUrls) {}
        RecentSnapshot recent = jdbc.query("""
                SELECT s.id, s.changes_summary::text AS changes_summary, prev.attachment_urls AS baseline_urls
                FROM advertisement_snapshot s
                LEFT JOIN advertisement_snapshot prev
                       ON prev.advertisement_id = s.advertisement_id
                      AND prev.version = s.version - 1
                WHERE s.advertisement_id = :adId
                  AND s.action_type IN ('CREATED', 'UPDATED')
                  AND s.created_at > NOW() - INTERVAL '5 minutes'
                ORDER BY s.version DESC LIMIT 1
                """,
                new MapSqlParameterSource("adId", advertisementId),
                (rs, row) -> new RecentSnapshot(
                        rs.getLong("id"),
                        rs.getString("changes_summary"),
                        toStringArray(rs, "baseline_urls")
                )
        ).stream().findFirst().orElse(null);

        if (recent != null) {
            List<String> baseline = recent.baselineUrls().length > 0 ? Arrays.asList(recent.baselineUrls()) : List.of();
            PhotoChange photoDiff  = buildPhotoDiff(baseline, currentUrls);
            List<ChangeEntry> newChanges = mergeSummary(stripPhotoEntries(fromJsonList(recent.changesSummaryJson())), photoDiff);
            jdbc.update("UPDATE advertisement_snapshot SET changes_summary = CAST(:s AS JSONB), attachment_urls = :u WHERE id = :id",
                    new MapSqlParameterSource()
                            .addValue("id", recent.id())
                            .addValue("s",  toChangesJson(newChanges))
                            .addValue("u",  urlArray));
        } else {
            String[] prevArr = jdbc.query(
                    "SELECT attachment_urls FROM advertisement_snapshot WHERE advertisement_id = :adId ORDER BY version DESC LIMIT 1",
                    new MapSqlParameterSource("adId", advertisementId),
                    (rs, row) -> toStringArray(rs, "attachment_urls")
            ).stream().findFirst().orElse(new String[0]);
            List<String> baseline = prevArr != null && prevArr.length > 0 ? Arrays.asList(prevArr) : List.of();
            PhotoChange photoDiff = buildPhotoDiff(baseline, currentUrls);
            jdbc.update("""
                    INSERT INTO advertisement_snapshot
                        (advertisement_id, title, description, attachment_urls, changed_by_user_id, action_type, version, changes_summary)
                    SELECT id, title, description, :attachmentUrls, :changedBy, 'UPDATED',
                        COALESCE((SELECT MAX(version) FROM advertisement_snapshot WHERE advertisement_id = :adId), 0) + 1,
                        CAST(:photoDiff AS JSONB)
                    FROM advertisement
                    WHERE id = :adId AND deleted_at IS NULL
                    """,
                    new MapSqlParameterSource()
                            .addValue("adId",           advertisementId)
                            .addValue("changedBy",      changedByUserId)
                            .addValue("photoDiff",      toChangesJson(photoDiff != null ? List.of(photoDiff) : null))
                            .addValue("attachmentUrls", urlArray)
            );
        }
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
        return historyProjection.queryAll(jdbc,
                new MapSqlParameterSource()
                        .addValue("adId",         advertisementId)
                        .addValue("filterUserId", showAll ? null : currentUserId));
    }

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId) {
        return jdbc.query(
                "SELECT title, description, attachment_urls FROM advertisement_snapshot WHERE id = :id",
                new MapSqlParameterSource("id", snapshotId),
                (rs, row) -> new SnapshotContent(
                        rs.getString("title"),
                        rs.getString("description"),
                        toStringArray(rs, "attachment_urls")
                )
        ).stream().findFirst();
    }

    private List<ChangeEntry> computeDiff(Advertisement ad, List<String> currentUrls) {
        List<List<ChangeEntry>> results = jdbc.query("""
                SELECT title, description, attachment_urls FROM advertisement_snapshot
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
                    List<String> prevUrls = List.of(toStringArray(rs, "attachment_urls"));
                    PhotoChange photoDiff = buildPhotoDiff(prevUrls, currentUrls);
                    if (photoDiff != null) parts.add(photoDiff);
                    return parts.isEmpty() ? null : parts;
                });
        return results.isEmpty() ? null : results.get(0);
    }

    public List<String> getActiveAttachmentUrls(Long adId) {
        return jdbc.queryForList(
                "SELECT url FROM attachment WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId AND deleted_at IS NULL",
                new MapSqlParameterSource("adId", adId),
                String.class
        );
    }

    private Array toSqlArray(List<String> list) {
        return jdbc.getJdbcOperations().execute(
                (Connection conn) -> conn.createArrayOf("text", list.toArray(new String[0]))
        );
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

    private static PhotoChange buildPhotoDiff(List<String> prevUrls, List<String> currUrls) {
        List<String> prevNames = prevUrls.stream().map(SnapshotService::filename).toList();
        List<String> currNames = currUrls.stream().map(SnapshotService::filename).toList();
        if (prevNames.equals(currNames)) return null;
        return new PhotoChange(prevNames, currNames);
    }

    static List<ChangeEntry> stripPhotoEntries(List<ChangeEntry> changes) {
        if (changes == null) return null;
        List<ChangeEntry> result = changes.stream()
                .filter(e -> !e.isPhoto())
                .toList();
        return result.isEmpty() ? null : result;
    }

    private static List<ChangeEntry> mergeSummary(List<ChangeEntry> text, PhotoChange photo) {
        if (text == null && photo == null) return null;
        if (photo == null) return text;
        List<ChangeEntry> result = new ArrayList<>(text != null ? text : List.of());
        result.add(photo);
        return result;
    }

    private static String filename(String url) {
        if (url == null || url.isBlank()) return "";
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private static String[] toStringArray(java.sql.ResultSet rs, String col) {
        try {
            java.sql.Array arr = rs.getArray(col);
            if (arr == null) return new String[0];
            return (String[]) arr.getArray();
        } catch (Exception e) {
            return new String[0];
        }
    }
}
