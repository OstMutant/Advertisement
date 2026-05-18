package org.ost.audit.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.SnapshotPayload;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.EntityDisplayNameResolver;
import org.ost.sqlengine.projection.SqlSelectField;
import org.ost.sqlengine.projection.SqlFixedQuery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.projection.SqlSelectFieldFactory.*;

public class ActivityProjection extends SqlFixedQuery<ActivityItemDto> {

    private static final String QUERY = """
            SELECT s.id                    AS snapshot_id,
                   s.entity_id             AS entity_id,
                   s.entity_type           AS entity_type,
                   s.action_type,
                   s.created_at,
                   FALSE                   AS entity_exists,
                   s.changes_summary::text AS changes_summary,
                   s.actor_id,
                   NULL::text              AS changed_by_name,
                   s.snapshot_data::text   AS snapshot_data
            FROM audit_log s
            WHERE s.actor_id = :userId
            ORDER BY s.created_at DESC
            LIMIT 20
            """;

    static final SqlSelectField<Long>    SNAPSHOT_ID     = longVal("s.id",          "snapshot_id");
    static final SqlSelectField<Long>    ENTITY_ID       = longVal("s.entity_id",   "entity_id");
    static final SqlSelectField<String>  ENTITY_TYPE     = str("s.entity_type",     "entity_type");
    static final SqlSelectField<String>  ACTION_TYPE_STR = str("s.action_type",     "action_type");
    static final SqlSelectField<Instant> CREATED_AT      = instant("s.created_at",  "created_at");
    static final SqlSelectField<Boolean> ENTITY_EXISTS   = bool("entity_exists",    "entity_exists");
    static final SqlSelectField<String>  CHANGES_SUMMARY = str("s.changes_summary", "changes_summary");
    static final SqlSelectField<Long>    ACTOR_ID        = longVal("s.actor_id",    "actor_id");
    static final SqlSelectField<String>  CHANGED_BY_NAME = str("changed_by_name",   "changed_by_name");
    static final SqlSelectField<String>  SNAPSHOT_DATA   = str("s.snapshot_data",   "snapshot_data");

    private final ObjectMapper objectMapper;
    private final List<EntityDisplayNameResolver> resolvers;

    public ActivityProjection(ObjectMapper objectMapper, List<EntityDisplayNameResolver> resolvers) {
        super(List.of(SNAPSHOT_ID, ENTITY_ID, ENTITY_TYPE, ACTION_TYPE_STR,
                      CREATED_AT, ENTITY_EXISTS, CHANGES_SUMMARY, ACTOR_ID, CHANGED_BY_NAME, SNAPSHOT_DATA));
        this.objectMapper = objectMapper;
        this.resolvers = resolvers;
    }

    @Override
    public String querySql() {
        return QUERY;
    }

    @Override
    public ActivityItemDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        String snapshotJson   = SNAPSHOT_DATA.extract(rs);
        EntityType entityType = EntityType.valueOf(ENTITY_TYPE.extract(rs));
        SnapshotPayload payload = new SnapshotPayload(snapshotJson);
        String displayName = resolvers.stream()
                .filter(r -> r.supports(entityType))
                .findFirst()
                .map(r -> r.resolveDisplayName(entityType, payload))
                .orElse("");
        return new ActivityItemDto(
                SNAPSHOT_ID.extract(rs),
                ENTITY_ID.extract(rs),
                entityType,
                displayName,
                ActionType.valueOf(ACTION_TYPE_STR.extract(rs)),
                CREATED_AT.extract(rs),
                ENTITY_EXISTS.extract(rs),
                parseChanges(CHANGES_SUMMARY.extract(rs)),
                ACTOR_ID.extract(rs),
                CHANGED_BY_NAME.extract(rs),
                payload
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
