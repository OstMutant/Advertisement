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
import org.ost.sqlengine.read.SqlFixedQuery;
import org.ost.sqlengine.read.SqlSelectField;
import org.ost.sqlengine.write.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.read.SqlSelectFieldFactory.*;

public final class AuditLogDescriptor implements SqlEntityDescriptor {

    public static final String TABLE = "audit_log";
    public static final String ALIAS = "al";

    public static final SqlSelectField<Long>    ID              = longVal(ALIAS + ".id",              "id");
    public static final SqlSelectField<Long>    ENTITY_ID       = longVal(ALIAS + ".entity_id",      "entity_id");
    public static final SqlSelectField<String>  ENTITY_TYPE     = str(ALIAS + ".entity_type",         "entity_type");
    public static final SqlSelectField<String>  ACTION_TYPE     = str(ALIAS + ".action_type",         "action_type");
    public static final SqlSelectField<Instant> CREATED_AT      = instant(ALIAS + ".created_at",      "created_at");
    public static final SqlSelectField<String>  SNAPSHOT_DATA   = str(ALIAS + ".snapshot_data",       "snapshot_data");
    public static final SqlSelectField<String>  CHANGES_SUMMARY = str(ALIAS + ".changes_summary",     "changes_summary");

    public static final class Read {
        private Read() {}

        public static final String SELECT_SNAPSHOT_DATA_BY_ID =
                "SELECT " + SNAPSHOT_DATA.columnName() + "::text" +
                " FROM "  + TABLE +
                " WHERE id = :id AND " + ENTITY_TYPE.columnName() + " = :entityType";

        public static final String SELECT_LAST_SNAPSHOT_DATA =
                "SELECT " + SNAPSHOT_DATA.columnName() + "::text" +
                " FROM "  + TABLE +
                " WHERE " + ENTITY_TYPE.columnName() + " = :entityType" +
                " AND "   + ENTITY_ID.columnName()   + " = :entityId" +
                " ORDER BY " + CREATED_AT.columnName() + " DESC LIMIT 1";

        public static final String SELECT_SNAPSHOT_CONTENT_BY_ID = """
                SELECT a.snapshot_data::text AS snapshot_data,
                       (SELECT COUNT(*) FROM audit_log b
                        WHERE b.entity_type = a.entity_type
                          AND b.entity_id   = a.entity_id
                          AND b.created_at <= a.created_at)::int AS version
                FROM audit_log a
                WHERE a.id = :id AND a.entity_type = :entityType
                """;

        public static final String SELECT_LAST_SNAPSHOT_ID =
                "SELECT id FROM " + TABLE +
                " WHERE " + ENTITY_TYPE.columnName() + " = :entityType" +
                " AND "   + ENTITY_ID.columnName()   + " = :entityId" +
                " ORDER BY " + CREATED_AT.columnName() + " DESC LIMIT 1";

        public static final String SELECT_CHANGES_SUMMARY =
                "SELECT " + CHANGES_SUMMARY.columnName() + "::text" +
                " FROM "  + TABLE + " WHERE id = :id";

        public static final String SELECT_PREVIOUS_SNAPSHOT_CONTENT = """
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
                """;

        public static MapSqlParameterSource snapshotByIdParams(Long id, EntityType entityType) {
            return new MapSqlParameterSource()
                    .addValue("id",         id)
                    .addValue("entityType", entityType.name());
        }

        public static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
            return new MapSqlParameterSource()
                    .addValue("entityType", entityType.name())
                    .addValue("entityId",   entityId);
        }

        public static MapSqlParameterSource idParams(Long id) {
            return new MapSqlParameterSource("id", id);
        }

        public static MapSqlParameterSource previousSnapshotContentParams(Long snapshotId, EntityType entityType) {
            return new MapSqlParameterSource()
                    .addValue("snapshotId", snapshotId)
                    .addValue("entityType", entityType.name());
        }

        public static SnapshotContent mapSnapshotContent(ResultSet rs) throws SQLException {
            return new SnapshotContent(
                    new SnapshotPayload(rs.getString("snapshot_data")),
                    rs.getInt("version"));
        }

        public static final class Activity {
            private Activity() {}

            public static final String ALIAS = "s";

            public static final SqlSelectField<Long>    SNAPSHOT_ID     = longVal(ALIAS + ".id",              "snapshot_id");
            public static final SqlSelectField<Long>    ENTITY_ID       = longVal(ALIAS + ".entity_id",      "entity_id");
            public static final SqlSelectField<String>  ENTITY_TYPE     = str(ALIAS + ".entity_type",         "entity_type");
            public static final SqlSelectField<String>  ACTION_TYPE     = str(ALIAS + ".action_type",         "action_type");
            public static final SqlSelectField<Instant> CREATED_AT      = instant(ALIAS + ".created_at",      "created_at");
            public static final SqlSelectField<Boolean> ENTITY_EXISTS   = bool("entity_exists",               "entity_exists");
            public static final SqlSelectField<String>  CHANGES_SUMMARY = str(ALIAS + ".changes_summary",     "changes_summary");
            public static final SqlSelectField<Long>    ACTOR_ID        = longVal(ALIAS + ".actor_id",        "actor_id");
            public static final SqlSelectField<String>  CHANGED_BY_NAME = str("changed_by_name",              "changed_by_name");
            public static final SqlSelectField<String>  SNAPSHOT_DATA   = str(ALIAS + ".snapshot_data",       "snapshot_data");

            public static final String QUERY = """
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
                    WHERE s.actor_id = :actorId
                    ORDER BY s.created_at DESC
                    LIMIT 20
                    """;

            public static final List<SqlSelectField<?>> FIELDS = List.of(
                    SNAPSHOT_ID, ENTITY_ID, ENTITY_TYPE, ACTION_TYPE,
                    CREATED_AT, ENTITY_EXISTS, CHANGES_SUMMARY, ACTOR_ID, CHANGED_BY_NAME, SNAPSHOT_DATA);

            public static MapSqlParameterSource byActorParams(Long actorId) {
                return new MapSqlParameterSource("actorId", actorId);
            }

            public static final class Projection extends SqlFixedQuery<ActivityItemDto> {

                private final ObjectMapper objectMapper;
                private final List<EntityDisplayNameResolver> resolvers;

                public Projection(ObjectMapper objectMapper, List<EntityDisplayNameResolver> resolvers) {
                    super(FIELDS);
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
                            ActionType.valueOf(ACTION_TYPE.extract(rs)),
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
        }

        public static final class History {
            private History() {}

            public static final String ALIAS = "n";

            public static final SqlSelectField<Long>    SNAPSHOT_ID        = longVal(ALIAS + ".id",                  "id");
            public static final SqlSelectField<Integer> VERSION            = intVal(ALIAS + ".version",              "version");
            public static final SqlSelectField<String>  ACTION_TYPE        = str(ALIAS + ".action_type",             "action_type");
            public static final SqlSelectField<Long>    ACTOR_ID           = longVal(ALIAS + ".actor_id",            "actor_id");
            public static final SqlSelectField<String>  CHANGED_BY_NAME    = str("changed_by_name",                  "changed_by_name");
            public static final SqlSelectField<Instant> CREATED_AT         = instant(ALIAS + ".created_at",          "created_at");
            public static final SqlSelectField<String>  SNAPSHOT_DATA      = str(ALIAS + ".snapshot_data",           "snapshot_data");
            public static final SqlSelectField<String>  CHANGES_SUMMARY    = str(ALIAS + ".changes_summary",         "changes_summary");
            public static final SqlSelectField<Long>    PREV_ID            = longVal(ALIAS + ".prev_id",             "prev_id");
            public static final SqlSelectField<String>  PREV_SNAPSHOT_DATA = str(ALIAS + ".prev_snapshot_data",      "prev_snapshot_data");

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

            public static final List<SqlSelectField<?>> FIELDS = List.of(
                    SNAPSHOT_ID, VERSION, ACTION_TYPE, ACTOR_ID, CHANGED_BY_NAME, CREATED_AT,
                    SNAPSHOT_DATA, CHANGES_SUMMARY, PREV_ID, PREV_SNAPSHOT_DATA);

            public static MapSqlParameterSource params(EntityType entityType, Long entityId, Long filterUserId) {
                return new MapSqlParameterSource()
                        .addValue("entityType",   entityType.name())
                        .addValue("entityId",     entityId)
                        .addValue("filterUserId", filterUserId);
            }

            public static final class Projection extends SqlFixedQuery<EntityHistoryDto> {

                private final ObjectMapper objectMapper;

                public Projection(ObjectMapper objectMapper) {
                    super(FIELDS);
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
                            ActionType.valueOf(ACTION_TYPE.extract(rs)),
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
        }
    }

    public static final class Write {
        private Write() {}
        public static final String TABLE              = AuditLogDescriptor.TABLE;
        public static final String ENTITY_TYPE        = AuditLogDescriptor.ENTITY_TYPE.columnName();
        public static final String ENTITY_ID          = AuditLogDescriptor.ENTITY_ID.columnName();
        public static final String ACTION_TYPE        = AuditLogDescriptor.ACTION_TYPE.columnName();
        public static final String SNAPSHOT_DATA      = AuditLogDescriptor.SNAPSHOT_DATA.columnName();
        public static final String CHANGES_SUMMARY    = AuditLogDescriptor.CHANGES_SUMMARY.columnName();
        public static final String ACTOR_ID           = "actor_id";

        public static final SqlWriteCommand INSERT = SqlWriteCommand.of(
                "INSERT INTO " + TABLE +
                " (" + ENTITY_TYPE + ", " + ENTITY_ID + ", " + ACTION_TYPE + ", " +
                       SNAPSHOT_DATA + ", " + CHANGES_SUMMARY + ", " + ACTOR_ID + ")" +
                " VALUES (:entityType, :entityId, :actionType," +
                " CAST(:snapshotData AS JSONB), CAST(:changes AS JSONB), :actorId)");

        public static final SqlWriteCommand UPDATE_CHANGES_SUMMARY = SqlWriteCommand.of(
                "UPDATE " + TABLE +
                " SET " + CHANGES_SUMMARY + " = CAST(:s AS JSONB) WHERE id = :id");

        public static final String DELETE_OLDER_THAN =
                "DELETE FROM " + TABLE +
                " WHERE " + AuditLogDescriptor.CREATED_AT.columnName() + " < NOW() - MAKE_INTERVAL(days => :days)";

        public static MapSqlParameterSource insertParams(EntityType entityType, Long entityId,
                                                          String actionType, String snapshotData,
                                                          String changesSummary, Long actorId) {
            return new MapSqlParameterSource()
                    .addValue("entityType",   entityType.name())
                    .addValue("entityId",     entityId)
                    .addValue("actionType",   actionType)
                    .addValue("snapshotData", snapshotData)
                    .addValue("changes",      changesSummary)
                    .addValue("actorId",      actorId);
        }

        public static MapSqlParameterSource updateChangesSummaryParams(Long snapshotId, String json) {
            return new MapSqlParameterSource()
                    .addValue("id", snapshotId)
                    .addValue("s",  json);
        }

        public static MapSqlParameterSource deleteOlderThanParams(int days) {
            return new MapSqlParameterSource("days", days);
        }
    }

    private AuditLogDescriptor() {}
}
