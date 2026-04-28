package org.ost.advertisement.repository.activity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.ActivityItemDto;
import org.ost.advertisement.entities.ActionType;
import org.ost.advertisement.model.ChangeEntry;
import org.ost.sqlengine.projection.SqlFieldDefinition;
import org.ost.sqlengine.projection.SqlFixedProjection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.projection.SqlFieldBuilder.*;

@Component
public class ActivityProjection extends SqlFixedProjection<ActivityItemDto> {

    private static final String SOURCE = "advertisement_snapshot s LEFT JOIN user_information u ON u.id = s.changed_by_user_id";

    private static final String QUERY = """
            WITH adv_act AS (
                SELECT s.id                                                             AS snapshot_id,
                       s.advertisement_id                                               AS entity_id,
                       'ADVERTISEMENT'                                                  AS entity_type,
                       s.title                                                          AS display_name,
                       s.action_type,
                       s.created_at,
                       EXISTS(SELECT 1 FROM advertisement a
                              WHERE a.id = s.advertisement_id AND a.deleted_at IS NULL) AS entity_exists,
                       s.changes_summary::text                                          AS changes_summary,
                       s.changed_by_user_id,
                       COALESCE(u.name, '—')                                            AS changed_by_name,
                       s.title                                                          AS snapshot_title,
                       s.description                                                    AS snapshot_description
                FROM advertisement_snapshot s
                LEFT JOIN user_information u ON u.id = s.changed_by_user_id
                WHERE s.changed_by_user_id = :userId
                ORDER BY s.created_at DESC LIMIT 20
            ),
            user_act AS (
                SELECT s.id                                     AS snapshot_id,
                       s.user_id                                AS entity_id,
                       'USER'                                   AS entity_type,
                       s.name                                   AS display_name,
                       s.action_type,
                       s.created_at,
                       EXISTS(SELECT 1 FROM user_information u2
                              WHERE u2.id = s.user_id)          AS entity_exists,
                       s.changes_summary::text                  AS changes_summary,
                       s.changed_by_user_id,
                       COALESCE(u.name, '—')                   AS changed_by_name,
                       NULL::text                               AS snapshot_title,
                       NULL::text                               AS snapshot_description
                FROM user_snapshot s
                LEFT JOIN user_information u ON u.id = s.changed_by_user_id
                WHERE s.changed_by_user_id = :userId OR s.user_id = :userId
                ORDER BY s.created_at DESC LIMIT 20
            ),
            photo_act AS (
                SELECT ps.id                                                              AS snapshot_id,
                       ps.advertisement_id                                                AS entity_id,
                       'ADVERTISEMENT'                                                    AS entity_type,
                       COALESCE(a.title, '—')                                             AS display_name,
                       'UPDATED'                                                          AS action_type,
                       ps.created_at,
                       EXISTS(SELECT 1 FROM advertisement a2
                              WHERE a2.id = ps.advertisement_id AND a2.deleted_at IS NULL) AS entity_exists,
                       ps.changes_summary::text                                           AS changes_summary,
                       ps.changed_by_user_id,
                       COALESCE(u.name, '—')                                              AS changed_by_name,
                       COALESCE(a.title, '—')                                             AS snapshot_title,
                       NULL::text                                                         AS snapshot_description
                FROM photo_snapshot ps
                LEFT JOIN advertisement a ON a.id = ps.advertisement_id
                LEFT JOIN user_information u ON u.id = ps.changed_by_user_id
                WHERE ps.changed_by_user_id = :userId
                  AND ps.changes_summary IS NOT NULL
                ORDER BY ps.created_at DESC LIMIT 20
            )
            SELECT * FROM adv_act
            UNION ALL
            SELECT * FROM user_act
            UNION ALL
            SELECT * FROM photo_act
            ORDER BY created_at DESC
            LIMIT 20
            """;

    static final SqlFieldDefinition<Long>    SNAPSHOT_ID        = id("s.id",                   "snapshot_id");
    static final SqlFieldDefinition<Long>    ENTITY_ID          = id("s.advertisement_id",     "entity_id");
    static final SqlFieldDefinition<String>  ENTITY_TYPE        = str("'ADVERTISEMENT'",       "entity_type");
    static final SqlFieldDefinition<String>  DISPLAY_NAME       = str("s.title",               "display_name");
    static final SqlFieldDefinition<String>  ACTION_TYPE_STR    = str("s.action_type",         "action_type");
    static final SqlFieldDefinition<Instant> CREATED_AT         = instant("s.created_at",      "created_at");
    static final SqlFieldDefinition<Boolean> ENTITY_EXISTS      = bool("EXISTS(...)",          "entity_exists");
    static final SqlFieldDefinition<String>  CHANGES_SUMMARY    = str("s.changes_summary",     "changes_summary");
    static final SqlFieldDefinition<Long>    CHANGED_BY_USER_ID = id("s.changed_by_user_id",   "changed_by_user_id");
    static final SqlFieldDefinition<String>  CHANGED_BY_NAME    = str("COALESCE(u.name,'—')",  "changed_by_name");

    @Qualifier("userSettingsObjectMapper") private final ObjectMapper objectMapper;

    public ActivityProjection(@Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        super(List.of(SNAPSHOT_ID, ENTITY_ID, ENTITY_TYPE, DISPLAY_NAME, ACTION_TYPE_STR,
                      CREATED_AT, ENTITY_EXISTS, CHANGES_SUMMARY, CHANGED_BY_USER_ID, CHANGED_BY_NAME),
              SOURCE);
        this.objectMapper = objectMapper;
    }

    @Override
    public String querySql() {
        return QUERY;
    }

    @Override
    public ActivityItemDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return new ActivityItemDto(
                SNAPSHOT_ID.extract(rs),
                ENTITY_ID.extract(rs),
                ENTITY_TYPE.extract(rs),
                DISPLAY_NAME.extract(rs),
                ActionType.valueOf(ACTION_TYPE_STR.extract(rs)),
                CREATED_AT.extract(rs),
                ENTITY_EXISTS.extract(rs),
                parseChanges(CHANGES_SUMMARY.extract(rs)),
                CHANGED_BY_USER_ID.extract(rs),
                CHANGED_BY_NAME.extract(rs),
                rs.getString("snapshot_title"),
                rs.getString("snapshot_description")
        );
    }

    private List<ChangeEntry> parseChanges(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            // photo_snapshot stores [{before:[...], after:[...]}] without type discriminator
            return parsePhotoChanges(json);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ChangeEntry> parsePhotoChanges(String json) {
        try {
            List<java.util.Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(m -> (ChangeEntry) new ChangeEntry.PhotoChange(
                            (List<String>) m.getOrDefault("before", List.of()),
                            (List<String>) m.getOrDefault("after",  List.of())
                    ))
                    .toList();
        } catch (Exception e2) {
            return List.of();
        }
    }
}
