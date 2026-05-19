package org.ost.audit.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotPayload;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.sqlengine.read.SqlSelectField;
import org.ost.sqlengine.read.SqlFixedQuery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.read.SqlSelectFieldFactory.*;

public class EntityHistoryProjection extends SqlFixedQuery<EntityHistoryDto> {

    private static final String QUERY = """
            WITH numbered AS (
                SELECT id,
                       ROW_NUMBER()             OVER (PARTITION BY entity_type, entity_id ORDER BY created_at)          AS version,
                       LAG(id)                  OVER (PARTITION BY entity_type, entity_id ORDER BY created_at)          AS prev_id,
                       LAG(snapshot_data::text) OVER (PARTITION BY entity_type, entity_id ORDER BY created_at)          AS prev_snapshot_data,
                       snapshot_data::text                                                                               AS snapshot_data,
                       action_type,
                       changes_summary::text                                                                             AS changes_summary,
                       actor_id,
                       created_at
                FROM audit_log
                WHERE entity_type = :entityType AND entity_id = :entityId
            )
            SELECT n.id, n.version::int, n.action_type,
                   n.actor_id,
                   NULL::text AS changed_by_name,
                   n.created_at,
                   n.snapshot_data,
                   n.changes_summary,
                   n.prev_id,
                   n.prev_snapshot_data
            FROM numbered n
            WHERE CAST(:filterUserId AS BIGINT) IS NULL OR n.actor_id = :filterUserId
            ORDER BY n.version DESC
            LIMIT 100
            """;

    static final SqlSelectField<Long>    SNAPSHOT_ID        = longVal("n.id",               "id");
    static final SqlSelectField<Integer> VERSION            = intVal("n.version",             "version");
    static final SqlSelectField<String>  ACTION_TYPE_STR    = str("n.action_type",            "action_type");
    static final SqlSelectField<Long>    ACTOR_ID           = longVal("n.actor_id",           "actor_id");
    static final SqlSelectField<String>  CHANGED_BY_NAME    = str("changed_by_name",          "changed_by_name");
    static final SqlSelectField<Instant> CREATED_AT         = instant("n.created_at",         "created_at");
    static final SqlSelectField<String>  SNAPSHOT_DATA      = str("n.snapshot_data",          "snapshot_data");
    static final SqlSelectField<String>  CHANGES_SUMMARY    = str("n.changes_summary",        "changes_summary");
    static final SqlSelectField<Long>    PREV_ID            = longVal("n.prev_id",            "prev_id");
    static final SqlSelectField<String>  PREV_SNAPSHOT_DATA = str("n.prev_snapshot_data",     "prev_snapshot_data");

    private final ObjectMapper objectMapper;

    public EntityHistoryProjection(ObjectMapper objectMapper) {
        super(List.of(SNAPSHOT_ID, VERSION, ACTION_TYPE_STR, ACTOR_ID, CHANGED_BY_NAME, CREATED_AT,
                      SNAPSHOT_DATA, CHANGES_SUMMARY,
                      PREV_ID, PREV_SNAPSHOT_DATA));
        this.objectMapper = objectMapper;
    }

    @Override
    public String querySql() {
        return QUERY;
    }

    @Override
    public EntityHistoryDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        String snapshotJson     = SNAPSHOT_DATA.extract(rs);
        String prevSnapshotJson = PREV_SNAPSHOT_DATA.extract(rs);
        return new EntityHistoryDto(
                SNAPSHOT_ID.extract(rs),
                VERSION.extract(rs),
                ActionType.valueOf(ACTION_TYPE_STR.extract(rs)),
                ACTOR_ID.extract(rs),
                CHANGED_BY_NAME.extract(rs),
                CREATED_AT.extract(rs),
                parseChanges(CHANGES_SUMMARY.extract(rs)),
                PREV_ID.extract(rs),
                new SnapshotPayload(snapshotJson),
                new SnapshotPayload(prevSnapshotJson)
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
