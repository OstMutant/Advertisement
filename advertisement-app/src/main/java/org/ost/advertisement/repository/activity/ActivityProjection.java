package org.ost.advertisement.repository.activity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.sqlengine.projection.SqlSelectField;
import org.ost.sqlengine.projection.SqlFixedQuery;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.projection.SqlSelectFieldFactory.*;

@Component
public class ActivityProjection extends SqlFixedQuery<ActivityItemDto> {

    private static final String QUERY = """
            WITH adv_act AS (
                SELECT s.id                                                             AS snapshot_id,
                       s.entity_id                                                      AS entity_id,
                       'ADVERTISEMENT'                                                  AS entity_type,
                       s.snapshot_data->>'title'                                        AS display_name,
                       s.action_type,
                       s.created_at,
                       EXISTS(SELECT 1 FROM advertisement a
                              WHERE a.id = s.entity_id AND a.deleted_at IS NULL)        AS entity_exists,
                       s.changes_summary::text                                          AS changes_summary,
                       s.changed_by_user_id,
                       COALESCE(u.name, '—')                                            AS changed_by_name,
                       s.snapshot_data->>'title'                                        AS snapshot_title,
                       s.snapshot_data->>'description'                                  AS snapshot_description,
                       NULL::text                                                       AS snapshot_email,
                       NULL::text                                                       AS snapshot_role
                FROM audit_log s
                LEFT JOIN user_information u ON u.id = s.changed_by_user_id
                WHERE s.entity_type = 'ADVERTISEMENT' AND s.changed_by_user_id = :userId
                ORDER BY s.created_at DESC LIMIT 20
            ),
            user_act AS (
                SELECT s.id                                                             AS snapshot_id,
                       s.entity_id                                                      AS entity_id,
                       s.entity_type                                                    AS entity_type,
                       COALESCE(s.snapshot_data->>'name', ui2.name, '—')               AS display_name,
                       s.action_type,
                       s.created_at,
                       EXISTS(SELECT 1 FROM user_information u2
                              WHERE u2.id = s.entity_id)                                AS entity_exists,
                       s.changes_summary::text                                          AS changes_summary,
                       s.changed_by_user_id,
                       COALESCE(u.name, '—')                                            AS changed_by_name,
                       NULL::text                                                       AS snapshot_title,
                       NULL::text                                                       AS snapshot_description,
                       s.snapshot_data->>'email'                                        AS snapshot_email,
                       s.snapshot_data->>'role'                                         AS snapshot_role
                FROM audit_log s
                LEFT JOIN user_information u   ON u.id   = s.changed_by_user_id
                LEFT JOIN user_information ui2 ON ui2.id = s.entity_id
                WHERE s.entity_type IN ('USER', 'USER_SETTINGS')
                  AND (s.changed_by_user_id = :userId OR s.entity_id = :userId)
                ORDER BY s.created_at DESC LIMIT 20
            )
            SELECT * FROM adv_act
            UNION ALL
            SELECT * FROM user_act
            ORDER BY created_at DESC
            LIMIT 20
            """;

    static final SqlSelectField<Long>    SNAPSHOT_ID        = longVal("s.id",                   "snapshot_id");
    static final SqlSelectField<Long>    ENTITY_ID          = longVal("s.entity_id",            "entity_id");
    static final SqlSelectField<String>  ENTITY_TYPE        = str("s.entity_type",         "entity_type");
    static final SqlSelectField<String>  DISPLAY_NAME       = str("display_name",          "display_name");
    static final SqlSelectField<String>  ACTION_TYPE_STR    = str("s.action_type",         "action_type");
    static final SqlSelectField<Instant> CREATED_AT         = instant("s.created_at",      "created_at");
    static final SqlSelectField<Boolean> ENTITY_EXISTS      = bool("entity_exists",        "entity_exists");
    static final SqlSelectField<String>  CHANGES_SUMMARY    = str("s.changes_summary",     "changes_summary");
    static final SqlSelectField<Long>    CHANGED_BY_USER_ID = longVal("s.changed_by_user_id",   "changed_by_user_id");
    static final SqlSelectField<String>  CHANGED_BY_NAME    = str("COALESCE(u.name,'—')",  "changed_by_name");

    @Qualifier("userSettingsObjectMapper") private final ObjectMapper objectMapper;

    public ActivityProjection(@Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        super(List.of(SNAPSHOT_ID, ENTITY_ID, ENTITY_TYPE, DISPLAY_NAME, ACTION_TYPE_STR,
                      CREATED_AT, ENTITY_EXISTS, CHANGES_SUMMARY, CHANGED_BY_USER_ID, CHANGED_BY_NAME));
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
                rs.getString("snapshot_description"),
                rs.getString("snapshot_email"),
                rs.getString("snapshot_role")
        );
    }

    private List<ChangeEntry> parseChanges(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
