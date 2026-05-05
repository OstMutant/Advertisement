package org.ost.advertisement.repository.advertisement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
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
public class AdvertisementHistoryProjection extends SqlFixedQuery<AdvertisementHistoryDto> {

    private static final String QUERY = """
            WITH numbered AS (
                SELECT id,
                       ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at)           AS version,
                       LAG(id)                            OVER (PARTITION BY entity_id ORDER BY created_at) AS prev_id,
                       LAG(snapshot_data->>'title')       OVER (PARTITION BY entity_id ORDER BY created_at) AS prev_title,
                       LAG(snapshot_data->>'description') OVER (PARTITION BY entity_id ORDER BY created_at) AS prev_description,
                       snapshot_data->>'title'            AS title,
                       snapshot_data->>'description'      AS description,
                       action_type,
                       changes_summary::text              AS changes_summary,
                       changed_by_user_id,
                       created_at
                FROM audit_log
                WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId
            )
            SELECT n.id, n.version::int, n.action_type,
                   COALESCE(u.name, '—') AS changed_by_name,
                   n.created_at, n.title, n.description, n.changes_summary,
                   n.prev_id, n.prev_title, n.prev_description
            FROM numbered n
            LEFT JOIN user_information u ON u.id = n.changed_by_user_id
            WHERE CAST(:filterUserId AS BIGINT) IS NULL OR n.changed_by_user_id = :filterUserId
            ORDER BY n.version DESC
            LIMIT 101
            """;

    static final SqlSelectField<Long>    SNAPSHOT_ID      = longVal("n.id",                         "id");
    static final SqlSelectField<Integer> VERSION          = intVal("n.version",                "version");
    static final SqlSelectField<String>  ACTION_TYPE_STR  = str("n.action_type",               "action_type");
    static final SqlSelectField<String>  CHANGED_BY_NAME  = str("COALESCE(u.name,'—')",        "changed_by_name");
    static final SqlSelectField<Instant> CREATED_AT       = instant("n.created_at",            "created_at");
    static final SqlSelectField<String>  TITLE            = str("n.title",                     "title");
    static final SqlSelectField<String>  DESCRIPTION      = str("n.description",               "description");
    static final SqlSelectField<String>  CHANGES_SUMMARY  = str("n.changes_summary",           "changes_summary");
    static final SqlSelectField<Long>    PREV_ID          = longVal("n.prev_id",               "prev_id");
    static final SqlSelectField<String>  PREV_TITLE       = str("n.prev_title",                "prev_title");
    static final SqlSelectField<String>  PREV_DESCRIPTION = str("n.prev_description",          "prev_description");

    @Qualifier("userSettingsObjectMapper") private final ObjectMapper objectMapper;

    public AdvertisementHistoryProjection(@Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        super(List.of(SNAPSHOT_ID, VERSION, ACTION_TYPE_STR, CHANGED_BY_NAME, CREATED_AT,
                      TITLE, DESCRIPTION, CHANGES_SUMMARY,
                      PREV_ID, PREV_TITLE, PREV_DESCRIPTION));
        this.objectMapper = objectMapper;
    }

    @Override
    public String querySql() {
        return QUERY;
    }

    @Override
    public AdvertisementHistoryDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return new AdvertisementHistoryDto(
                SNAPSHOT_ID.extract(rs),
                VERSION.extract(rs),
                ActionType.valueOf(ACTION_TYPE_STR.extract(rs)),
                CHANGED_BY_NAME.extract(rs),
                CREATED_AT.extract(rs),
                TITLE.extract(rs),
                DESCRIPTION.extract(rs),
                parseChanges(CHANGES_SUMMARY.extract(rs)),
                PREV_ID.extract(rs),
                PREV_TITLE.extract(rs),
                PREV_DESCRIPTION.extract(rs)
        );
    }

    private List<ChangeEntry> parseChanges(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception _) {
            return List.of();
        }
    }
}
