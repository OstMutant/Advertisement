package org.ost.audit.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotContent;
import org.ost.platform.audit.dto.SnapshotPayload;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.EntityDisplayNameResolver;
import org.ost.sqlengine.SqlEntityDescriptor;
import org.ost.sqlengine.SqlParams;
import org.ost.sqlengine.read.SqlFixedQuery;
import org.ost.sqlengine.read.SqlSelectField;
import org.ost.sqlengine.exec.SqlCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.read.SqlSelectFieldFactory.*;

public final class AuditLogDescriptor implements SqlEntityDescriptor {

    public static final String TABLE = "audit_log";
    public static final String ALIAS = "al";

    public static final SqlSelectField<Long>    ID              = longCol(ALIAS,    "id");
    public static final SqlSelectField<Long>    ENTITY_ID       = longCol(ALIAS,    "entity_id");
    public static final SqlSelectField<String>  ENTITY_TYPE     = strCol(ALIAS,     "entity_type");
    public static final SqlSelectField<String>  ACTION_TYPE     = strCol(ALIAS,     "action_type");
    public static final SqlSelectField<Instant> CREATED_AT      = instantCol(ALIAS, "created_at");
    public static final SqlSelectField<String>  SNAPSHOT_DATA   = strCol(ALIAS,     "snapshot_data");
    public static final SqlSelectField<String>  CHANGES_SUMMARY = strCol(ALIAS,     "changes_summary");
    public static final SqlSelectField<Long>    ACTOR_ID        = longCol(ALIAS,    "actor_id");
    public static final SqlSelectField<String>  CHANGED_BY_NAME = str("changed_by_name", "changed_by_name");

    public static final class Read {
        private Read() {}

        private static final TypeReference<List<ChangeEntry>> CHANGES_TYPE = new TypeReference<>() {};

        public static final SqlCommand SELECT_SNAPSHOT_DATA_BY_ID = SqlCommand.of(
                "SELECT " + SNAPSHOT_DATA.columnName() + "::text" +
                " FROM "  + TABLE +
                " WHERE " + ID.columnName() + " = :id AND " + ENTITY_TYPE.columnName() + " = :entityType");

        public static final SqlCommand SELECT_LAST_SNAPSHOT_DATA = SqlCommand.of(
                "SELECT " + SNAPSHOT_DATA.columnName() + "::text" +
                " FROM "  + TABLE +
                " WHERE " + ENTITY_TYPE.columnName() + " = :entityType" +
                " AND "   + ENTITY_ID.columnName()   + " = :entityId" +
                " ORDER BY " + CREATED_AT.columnName() + " DESC LIMIT 1");

        public static final SqlCommand SELECT_SNAPSHOT_CONTENT_BY_ID = SqlCommand.of("""
                SELECT a.snapshot_data::text AS snapshot_data,
                       (SELECT COUNT(*) FROM audit_log b
                        WHERE b.entity_type = a.entity_type
                          AND b.entity_id   = a.entity_id
                          AND b.created_at <= a.created_at)::int AS version
                FROM audit_log a
                WHERE a.id = :id AND a.entity_type = :entityType
                """);

        public static final SqlCommand SELECT_LAST_SNAPSHOT_ID = SqlCommand.of(
                "SELECT " + ID.columnName() + " FROM " + TABLE +
                " WHERE " + ENTITY_TYPE.columnName() + " = :entityType" +
                " AND "   + ENTITY_ID.columnName()   + " = :entityId" +
                " ORDER BY " + CREATED_AT.columnName() + " DESC LIMIT 1");

        public static final SqlCommand SELECT_CHANGES_SUMMARY = SqlCommand.of(
                "SELECT " + CHANGES_SUMMARY.columnName() + "::text" +
                " FROM "  + TABLE + " WHERE " + ID.columnName() + " = :id");

        public static final SqlCommand SELECT_PREVIOUS_SNAPSHOT_CONTENT = SqlCommand.of("""
                SELECT prev.snapshot_data::text AS snapshot_data,
                       (SELECT COUNT(*) FROM audit_log b
                        WHERE b.entity_type = prev.entity_type
                          AND b.entity_id   = prev.entity_id
                          AND b.created_at <= prev.created_at)::int AS version
                FROM audit_log cur
                JOIN LATERAL (
                    SELECT entity_id, entity_type, snapshot_data, created_at
                    FROM audit_log
                    WHERE entity_type = :entityType
                      AND entity_id = cur.entity_id
                      AND created_at < cur.created_at
                    ORDER BY created_at DESC LIMIT 1
                ) prev ON true
                WHERE cur.id = :snapshotId AND cur.entity_type = :entityType
                """);

        public static MapSqlParameterSource snapshotByIdParams(Long id, EntityType entityType) {
            return SqlParams.with("id", id).add("entityType", entityType.name());
        }

        public static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
            return SqlParams.with("entityType", entityType.name()).add("entityId", entityId);
        }

        public static MapSqlParameterSource idParams(Long id) {
            return SqlParams.of("id", id);
        }

        public static MapSqlParameterSource previousSnapshotContentParams(Long snapshotId, EntityType entityType) {
            return SqlParams.with("snapshotId", snapshotId).add("entityType", entityType.name());
        }

        public static SnapshotContent mapSnapshotContent(ResultSet rs) throws SQLException {
            return new SnapshotContent(
                    new SnapshotPayload(rs.getString("snapshot_data")),
                    rs.getInt("version"));
        }

        private static abstract class JsonProjection<T> extends SqlFixedQuery<T> {
            protected final ObjectMapper objectMapper;

            protected JsonProjection(List<SqlSelectField<?>> fields, ObjectMapper objectMapper) {
                super(fields);
                this.objectMapper = objectMapper;
            }

            protected List<ChangeEntry> parseChanges(String json) {
                if (json == null || json.isBlank()) return List.of();
                try {
                    return objectMapper.readValue(json, CHANGES_TYPE);
                } catch (Exception _) {
                    return List.of();
                }
            }
        }

        public static final class Activity {
            private Activity() {}

            public static final SqlSelectField<Long>    SNAPSHOT_ID   = longVal(ALIAS + ".id", "snapshot_id");
            public static final SqlSelectField<Boolean> ENTITY_EXISTS = bool("entity_exists", "entity_exists");

            public static final String QUERY = """
                    SELECT al.id                    AS snapshot_id,
                           al.entity_id             AS entity_id,
                           al.entity_type           AS entity_type,
                           al.action_type,
                           al.created_at,
                           FALSE                    AS entity_exists,
                           al.changes_summary::text AS changes_summary,
                           al.actor_id,
                           NULL::text               AS changed_by_name,
                           al.snapshot_data::text   AS snapshot_data
                    FROM audit_log al
                    WHERE al.actor_id = :actorId
                    ORDER BY al.created_at DESC
                    LIMIT 20
                    """;

            public static final List<SqlSelectField<?>> FIELDS = List.of(
                    SNAPSHOT_ID, ENTITY_ID, ENTITY_TYPE, ACTION_TYPE,
                    CREATED_AT, ENTITY_EXISTS, CHANGES_SUMMARY, ACTOR_ID, CHANGED_BY_NAME, SNAPSHOT_DATA);

            public static MapSqlParameterSource byActorParams(Long actorId) {
                return SqlParams.of("actorId", actorId);
            }

            public static final class Projection extends JsonProjection<ActivityItemDto> {

                private final List<EntityDisplayNameResolver> resolvers;

                public Projection(ObjectMapper objectMapper, List<EntityDisplayNameResolver> resolvers) {
                    super(FIELDS, objectMapper);
                    this.resolvers = resolvers;
                }

                @Override
                public String querySql() {
                    return QUERY;
                }

                @Override
                public ActivityItemDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
                    EntityType entityType = EntityType.valueOf(ENTITY_TYPE.extract(rs));
                    SnapshotPayload payload = new SnapshotPayload(SNAPSHOT_DATA.extract(rs));
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
                            ActionType.valueOf(ACTION_TYPE.extract(rs)),
                            CREATED_AT.extract(rs),
                            ENTITY_EXISTS.extract(rs),
                            parseChanges(CHANGES_SUMMARY.extract(rs)),
                            ACTOR_ID.extract(rs),
                            CHANGED_BY_NAME.extract(rs),
                            payload
                    );
                }
            }
        }

        public static final class History {
            private History() {}

            public static final SqlSelectField<Integer> VERSION            = intCol(ALIAS, "version");
            public static final SqlSelectField<Long>    PREV_ID            = longCol(ALIAS, "prev_id");
            public static final SqlSelectField<String>  PREV_SNAPSHOT_DATA = strCol(ALIAS, "prev_snapshot_data");

            public static final String QUERY = """
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
                    SELECT al.id, al.version::int, al.action_type,
                           al.actor_id,
                           NULL::text AS changed_by_name,
                           al.created_at,
                           al.snapshot_data,
                           al.changes_summary,
                           al.prev_id,
                           al.prev_snapshot_data
                    FROM numbered al
                    WHERE CAST(:filterUserId AS BIGINT) IS NULL OR al.actor_id = :filterUserId
                    ORDER BY al.version DESC
                    LIMIT 100
                    """;

            public static final List<SqlSelectField<?>> FIELDS = List.of(
                    ID, VERSION, ACTION_TYPE, ACTOR_ID, CHANGED_BY_NAME, CREATED_AT,
                    SNAPSHOT_DATA, CHANGES_SUMMARY, PREV_ID, PREV_SNAPSHOT_DATA);

            public static MapSqlParameterSource params(EntityType entityType, Long entityId, Long filterUserId) {
                return SqlParams.with("entityType",   entityType.name())
                                .add("entityId",     entityId)
                                .add("filterUserId", filterUserId);
            }

            public static final class Projection extends JsonProjection<EntityHistoryDto> {

                public Projection(ObjectMapper objectMapper) {
                    super(FIELDS, objectMapper);
                }

                @Override
                public String querySql() {
                    return QUERY;
                }

                @Override
                public EntityHistoryDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
                    return new EntityHistoryDto(
                            ID.extract(rs),
                            VERSION.extract(rs),
                            ActionType.valueOf(ACTION_TYPE.extract(rs)),
                            ACTOR_ID.extract(rs),
                            CHANGED_BY_NAME.extract(rs),
                            CREATED_AT.extract(rs),
                            parseChanges(CHANGES_SUMMARY.extract(rs)),
                            PREV_ID.extract(rs),
                            new SnapshotPayload(SNAPSHOT_DATA.extract(rs)),
                            new SnapshotPayload(PREV_SNAPSHOT_DATA.extract(rs))
                    );
                }
            }
        }
    }

    public static final class Write {
        private Write() {}

        public static final SqlCommand INSERT = SqlCommand.of(
                "INSERT INTO " + TABLE +
                " (" + ENTITY_TYPE.columnName() + ", " + ENTITY_ID.columnName() + ", " + ACTION_TYPE.columnName() + ", " +
                       SNAPSHOT_DATA.columnName() + ", " + CHANGES_SUMMARY.columnName() + ", " + ACTOR_ID.columnName() + ")" +
                " VALUES (:entityType, :entityId, :actionType," +
                " CAST(:snapshotData AS JSONB), CAST(:changes AS JSONB), :actorId)");

        public static final SqlCommand UPDATE_CHANGES_SUMMARY = SqlCommand.of(
                "UPDATE " + TABLE +
                " SET " + CHANGES_SUMMARY.columnName() + " = CAST(:s AS JSONB) WHERE id = :id");

        public static final SqlCommand DELETE_OLDER_THAN = SqlCommand.of(
                "DELETE FROM " + TABLE +
                " WHERE " + CREATED_AT.columnName() + " < NOW() - MAKE_INTERVAL(days => :days)");

        public static MapSqlParameterSource insertParams(EntityType entityType, Long entityId,
                                                          String actionType, String snapshotData,
                                                          String changesSummary, Long actorId) {
            return SqlParams.with("entityType",   entityType.name())
                            .add("entityId",     entityId)
                            .add("actionType",   actionType)
                            .add("snapshotData", snapshotData)
                            .add("changes",      changesSummary)
                            .add("actorId",      actorId);
        }

        public static MapSqlParameterSource updateChangesSummaryParams(Long snapshotId, String json) {
            return SqlParams.with("id", snapshotId).add("s", json);
        }

        public static MapSqlParameterSource deleteOlderThanParams(int days) {
            return SqlParams.of("days", days);
        }
    }

    private AuditLogDescriptor() {}
}
