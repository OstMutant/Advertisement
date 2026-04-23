package org.ost.advertisement.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementHistoryDto;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.entities.ActionType;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnapshotService {

    public record SnapshotContent(String title, String description, String[] attachmentUrls) {}
    public record UserSnapshotState(Long userId, String name, Role role) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper               objectMapper;

    public void captureAdvertisement(Advertisement ad, ActionType actionType, Long changedByUserId) {
        List<String> currentUrls = getActiveAttachmentUrls(ad.getId());
        String changesSummary = switch (actionType) {
            case CREATED -> "назва: \"" + truncate(ad.getTitle()) + "\"; опис: \"" + truncate(ad.getDescription()) + "\"";
            case UPDATED -> computeDiff(ad, currentUrls);
            case DELETED -> null;
        };

        Array urlArray = toSqlArray(currentUrls);

        jdbc.update("""
                INSERT INTO advertisement_snapshot
                    (advertisement_id, title, description, attachment_urls, changed_by_user_id, action_type, version, changes_summary)
                VALUES (:adId, :title, :description, :attachmentUrls, :changedBy, :actionType,
                    COALESCE((SELECT MAX(version) FROM advertisement_snapshot WHERE advertisement_id = :adId), 0) + 1,
                    :changesSummary)
                """,
                new MapSqlParameterSource()
                        .addValue("adId",           ad.getId())
                        .addValue("title",          ad.getTitle())
                        .addValue("description",    ad.getDescription())
                        .addValue("attachmentUrls", urlArray)
                        .addValue("changedBy",      changedByUserId)
                        .addValue("actionType",     actionType.name())
                        .addValue("changesSummary", changesSummary)
        );
    }

    public void captureUser(User user, ActionType actionType, Long changedByUserId) {
        captureUser(user, null, actionType, changedByUserId);
    }

    public void captureUser(User user, User before, ActionType actionType, Long changedByUserId) {
        String changesSummary = switch (actionType) {
            case CREATED -> "ім'я: \"" + truncate(user.getName()) + "\"; email: " + truncate(user.getEmail()) + "; роль: " + user.getRole().name();
            case UPDATED -> computeUserDiff(user, before);
            case DELETED -> null;
        };
        insertUserSnapshot(user, null, actionType, changedByUserId, changesSummary);
    }

    public void captureSettingsChange(User user, UserSettings oldSettings, UserSettings newSettings, Long changedByUserId) {
        List<String> parts = new ArrayList<>();
        if (oldSettings.getAdsPageSize() != newSettings.getAdsPageSize()) {
            parts.add("оголошень на сторінці: " + oldSettings.getAdsPageSize() + " → " + newSettings.getAdsPageSize());
        }
        if (oldSettings.getUsersPageSize() != newSettings.getUsersPageSize()) {
            parts.add("користувачів на сторінці: " + oldSettings.getUsersPageSize() + " → " + newSettings.getUsersPageSize());
        }
        if (parts.isEmpty()) return;
        insertUserSnapshot(user, toJson(oldSettings), ActionType.UPDATED, changedByUserId, String.join("; ", parts));
    }

    private void insertUserSnapshot(User user, String settingsJson, ActionType actionType, Long changedByUserId, String changesSummary) {
        jdbc.update("""
                INSERT INTO user_snapshot
                    (user_id, name, email, role, settings, changed_by_user_id, action_type, version, changes_summary)
                VALUES (:userId, :name, :email, :role, CAST(:settings AS JSONB), :changedBy, :actionType,
                    COALESCE((SELECT MAX(version) FROM user_snapshot WHERE user_id = :userId), 0) + 1,
                    :changesSummary)
                """,
                new MapSqlParameterSource()
                        .addValue("userId",         user.getId())
                        .addValue("name",           user.getName())
                        .addValue("email",          user.getEmail())
                        .addValue("role",           user.getRole().name())
                        .addValue("settings",       settingsJson)
                        .addValue("changedBy",      changedByUserId)
                        .addValue("actionType",     actionType.name())
                        .addValue("changesSummary", changesSummary)
        );
    }

    public Optional<UserSettings> getSettingsFromSnapshot(Long snapshotId) {
        return jdbc.query(
                "SELECT settings FROM user_snapshot WHERE id = :id AND settings IS NOT NULL",
                new MapSqlParameterSource("id", snapshotId),
                (rs, row) -> fromJson(rs.getString("settings"), UserSettings.class)
        ).stream().findFirst();
    }

    /** Returns the user's state from the snapshot BEFORE the given one (version - 1). */
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

    private String computeUserDiff(User user, User before) {
        if (before != null) {
            List<String> parts = new ArrayList<>();
            if (!Objects.equals(user.getName(), before.getName())) {
                parts.add("ім'я: \"" + truncate(before.getName()) + "\" → \"" + truncate(user.getName()) + "\"");
            }
            if (!Objects.equals(user.getEmail(), before.getEmail())) {
                parts.add("email: \"" + truncate(before.getEmail()) + "\" → \"" + truncate(user.getEmail()) + "\"");
            }
            if (!Objects.equals(user.getRole().name(), before.getRole().name())) {
                parts.add("роль: " + before.getRole().name() + " → " + user.getRole().name());
            }
            return parts.isEmpty() ? null : String.join("; ", parts);
        }
        List<String> results = jdbc.query("""
                SELECT name, email, role FROM user_snapshot
                WHERE user_id = :userId
                ORDER BY version DESC LIMIT 1
                """,
                new MapSqlParameterSource("userId", user.getId()),
                (rs, row) -> {
                    List<String> parts = new ArrayList<>();
                    if (!Objects.equals(user.getName(), rs.getString("name"))) {
                        parts.add("ім'я: \"" + truncate(rs.getString("name")) + "\" → \"" + truncate(user.getName()) + "\"");
                    }
                    if (!Objects.equals(user.getEmail(), rs.getString("email"))) {
                        parts.add("email: \"" + truncate(rs.getString("email")) + "\" → \"" + truncate(user.getEmail()) + "\"");
                    }
                    if (!Objects.equals(user.getRole().name(), rs.getString("role"))) {
                        parts.add("роль: " + rs.getString("role") + " → " + user.getRole().name());
                    }
                    return parts.isEmpty() ? null : String.join("; ", parts);
                });
        return results.isEmpty() ? null : results.get(0);
    }

    public void captureAdvertisementAttachmentChange(Long advertisementId, Long changedByUserId) {
        List<String> currentUrls = getActiveAttachmentUrls(advertisementId);
        Array urlArray = toSqlArray(currentUrls);

        record RecentSnapshot(Long id, String changesSummary, String[] baselineUrls) {}
        RecentSnapshot recent = jdbc.query("""
                SELECT s.id, s.changes_summary, prev.attachment_urls AS baseline_urls
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
            String photoDiff  = buildPhotoDiffEntry(baseline, currentUrls);
            String newSummary = mergeSummary(stripPhotoEntries(recent.changesSummary()), photoDiff);
            jdbc.update("UPDATE advertisement_snapshot SET changes_summary = :s, attachment_urls = :u WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", recent.id()).addValue("s", newSummary).addValue("u", urlArray));
        } else {
            String[] prevArr = jdbc.query(
                    "SELECT attachment_urls FROM advertisement_snapshot WHERE advertisement_id = :adId ORDER BY version DESC LIMIT 1",
                    new MapSqlParameterSource("adId", advertisementId),
                    (rs, row) -> toStringArray(rs, "attachment_urls")
            ).stream().findFirst().orElse(new String[0]);
            List<String> baseline = prevArr != null && prevArr.length > 0 ? Arrays.asList(prevArr) : List.of();
            String photoDiff = buildPhotoDiffEntry(baseline, currentUrls);
            jdbc.update("""
                    INSERT INTO advertisement_snapshot
                        (advertisement_id, title, description, attachment_urls, changed_by_user_id, action_type, version, changes_summary)
                    SELECT id, title, description, :attachmentUrls, :changedBy, 'UPDATED',
                        COALESCE((SELECT MAX(version) FROM advertisement_snapshot WHERE advertisement_id = :adId), 0) + 1,
                        :photoDiff
                    FROM advertisement
                    WHERE id = :adId AND deleted_at IS NULL
                    """,
                    new MapSqlParameterSource()
                            .addValue("adId",           advertisementId)
                            .addValue("changedBy",      changedByUserId)
                            .addValue("photoDiff",      photoDiff)
                            .addValue("attachmentUrls", urlArray)
            );
        }
    }

    public void appendNoteToLastSnapshot(Long advertisementId, String note) {
        jdbc.update("""
                UPDATE advertisement_snapshot
                SET changes_summary = CASE
                    WHEN changes_summary IS NULL THEN :note
                    ELSE changes_summary || '; ' || :note
                END
                WHERE id = (
                    SELECT id FROM advertisement_snapshot
                    WHERE advertisement_id = :advertisementId
                    ORDER BY version DESC LIMIT 1
                )
                """,
                new MapSqlParameterSource()
                        .addValue("advertisementId", advertisementId)
                        .addValue("note",             note)
        );
    }

    public List<AdvertisementHistoryDto> getAdvertisementHistory(Long advertisementId, Long currentUserId, boolean showAll) {
        Long filterUserId = showAll ? null : currentUserId;
        return jdbc.query("""
                SELECT s.id, s.version, s.action_type, s.title, s.description,
                       s.changes_summary, s.attachment_urls, s.created_at,
                       COALESCE(u.name, '—') AS changed_by_name,
                       prev.id   AS prev_id,   prev.title       AS prev_title,
                       prev.description       AS prev_description,
                       prev.attachment_urls   AS prev_urls
                FROM advertisement_snapshot s
                LEFT JOIN user_information u ON u.id = s.changed_by_user_id
                LEFT JOIN advertisement_snapshot prev
                       ON prev.advertisement_id = s.advertisement_id
                      AND prev.version = s.version - 1
                WHERE s.advertisement_id = :adId
                  AND (CAST(:filterUserId AS BIGINT) IS NULL OR s.changed_by_user_id = :filterUserId)
                ORDER BY s.version DESC
                LIMIT 50
                """,
                new MapSqlParameterSource()
                        .addValue("adId",         advertisementId)
                        .addValue("filterUserId", filterUserId),
                (rs, row) -> {
                    String[] urls     = toStringArray(rs, "attachment_urls");
                    String[] prevUrls = toStringArray(rs, "prev_urls");
                    Long prevId = rs.getLong("prev_id");
                    if (rs.wasNull()) prevId = null;
                    return new AdvertisementHistoryDto(
                            rs.getLong("id"),
                            rs.getInt("version"),
                            ActionType.valueOf(rs.getString("action_type")),
                            rs.getString("changed_by_name"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("changes_summary"),
                            urls,
                            prevId,
                            rs.getString("prev_title"),
                            rs.getString("prev_description"),
                            prevUrls
                    );
                });
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

    private String computeDiff(Advertisement ad, List<String> currentUrls) {
        List<String> results = jdbc.query("""
                SELECT title, description, attachment_urls FROM advertisement_snapshot
                WHERE advertisement_id = :adId
                ORDER BY version DESC LIMIT 1
                """,
                new MapSqlParameterSource("adId", ad.getId()),
                (rs, row) -> {
                    List<String> parts = new ArrayList<>();
                    String prevTitle = rs.getString("title");
                    String prevDesc  = rs.getString("description");
                    if (!Objects.equals(ad.getTitle(), prevTitle)) {
                        parts.add("назва: \"" + truncate(prevTitle) + "\" → \"" + truncate(ad.getTitle()) + "\"");
                    }
                    if (!Objects.equals(ad.getDescription(), prevDesc)) {
                        parts.add("опис: \"" + truncate(prevDesc) + "\" → \"" + truncate(ad.getDescription()) + "\"");
                    }
                    List<String> prevUrls = List.of(toStringArray(rs, "attachment_urls"));
                    String photoDiff = buildPhotoDiffEntry(prevUrls, currentUrls);
                    if (photoDiff != null) parts.add(photoDiff);
                    return parts.isEmpty() ? null : String.join("; ", parts);
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildPhotoDiffEntry(List<String> prevUrls, List<String> currUrls) {
        List<String> prevNames = prevUrls.stream().map(SnapshotService::filename).toList();
        List<String> currNames = currUrls.stream().map(SnapshotService::filename).toList();
        if (prevNames.equals(currNames)) return null;
        return "фото: [" + String.join(", ", prevNames) + "] → [" + String.join(", ", currNames) + "]";
    }

    static String stripPhotoEntries(String summary) {
        if (summary == null) return null;
        String result = Arrays.stream(summary.split("; "))
                .filter(p -> !p.startsWith("фото:") && !p.startsWith("видалено фото") && !p.startsWith("додано фото"))
                .collect(Collectors.joining("; "));
        return result.isBlank() ? null : result;
    }

    private static String mergeSummary(String text, String photo) {
        if (text == null && photo == null) return null;
        if (text == null) return photo;
        if (photo == null) return text;
        return text + "; " + photo;
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
