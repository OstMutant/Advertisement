package org.ost.audit.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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
import org.ost.sqlengine.common.SqlCommand;
import org.ost.sqlengine.common.SqlDescriptorField;
import org.ost.sqlengine.read.SqlFixedQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.common.SqlDescriptorFieldFactory.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuditLogDescriptor implements SqlEntityDescriptor {

    public static final String TABLE = "audit_log";
    public static final String ALIAS = "al";

    public static final SqlDescriptorField<Long>    ID              = longCol(ALIAS,    "id");
    public static final SqlDescriptorField<Long>    ENTITY_ID       = longCol(ALIAS,    "entity_id");
    public static final SqlDescriptorField<String>  ENTITY_TYPE     = strCol(ALIAS,     "entity_type");
    public static final SqlDescriptorField<String>  ACTION_TYPE     = strCol(ALIAS,     "action_type");
    public static final SqlDescriptorField<Instant> CREATED_AT      = instantCol(ALIAS, "created_at");
    public static final SqlDescriptorField<String>  SNAPSHOT_DATA   = strCol(ALIAS,     "snapshot_data");
    public static final SqlDescriptorField<String>  CHANGES_SUMMARY = strCol(ALIAS,     "changes_summary");
    public static final SqlDescriptorField<Long>    ACTOR_ID        = longCol(ALIAS,    "actor_id");
    public static final SqlDescriptorField<String>  CHANGED_BY_NAME = strCol("changed_by_name");

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Read {

        private static final TypeReference<List<ChangeEntry>> CHANGES_TYPE = new TypeReference<>() {};

        public static final SqlCommand SELECT_SNAPSHOT_DATA_BY_ID = SqlCommand.of(
                "SELECT {snapshotData}::text FROM {table} WHERE {id} = :id AND {entityType} = :entityType",
                "snapshotData", SNAPSHOT_DATA.columnName(),
                "table",        TABLE,
                "id",           ID.columnName(),
                "entityType",   ENTITY_TYPE.columnName());

        public static final SqlCommand SELECT_LAST_SNAPSHOT_DATA = SqlCommand.of(
                "SELECT {snapshotData}::text FROM {table}" +
                " WHERE {entityType} = :entityType AND {entityId} = :entityId ORDER BY {createdAt} DESC LIMIT 1",
                "snapshotData", SNAPSHOT_DATA.columnName(),
                "table",        TABLE,
                "entityType",   ENTITY_TYPE.columnName(),
                "entityId",     ENTITY_ID.columnName(),
                "createdAt",    CREATED_AT.columnName());

        public static final SqlCommand SELECT_SNAPSHOT_CONTENT_BY_ID = SqlCommand.of("""
                SELECT a.{snapshotData}::text AS {snapshotData},
                       (SELECT COUNT(*) FROM audit_log b
                        WHERE b.{entityType} = a.{entityType}
                          AND b.{entityId}   = a.{entityId}
                          AND b.{createdAt} <= a.{createdAt})::int AS version
                FROM audit_log a
                WHERE a.{id} = :id AND a.{entityType} = :entityType
                """,
                "snapshotData", SNAPSHOT_DATA.columnName(),
                "entityType",   ENTITY_TYPE.columnName(),
                "entityId",     ENTITY_ID.columnName(),
                "createdAt",    CREATED_AT.columnName(),
                "id",           ID.columnName());

        public static final SqlCommand SELECT_LAST_SNAPSHOT_ID = SqlCommand.of(
                "SELECT {id} FROM {table}" +
                " WHERE {entityType} = :entityType AND {entityId} = :entityId ORDER BY {createdAt} DESC LIMIT 1",
                "id",         ID.columnName(),
                "table",      TABLE,
                "entityType", ENTITY_TYPE.columnName(),
                "entityId",   ENTITY_ID.columnName(),
                "createdAt",  CREATED_AT.columnName());

        public static final SqlCommand SELECT_CHANGES_SUMMARY = SqlCommand.of(
                "SELECT {changesSummary}::text FROM {table} WHERE {id} = :id",
                "changesSummary", CHANGES_SUMMARY.columnName(),
                "table",          TABLE,
                "id",             ID.columnName());

        public static final SqlCommand SELECT_PREVIOUS_SNAPSHOT_CONTENT = SqlCommand.of("""
                SELECT prev.{snapshotData}::text AS {snapshotData},
                       (SELECT COUNT(*) FROM audit_log b
                        WHERE b.{entityType} = prev.{entityType}
                          AND b.{entityId}   = prev.{entityId}
                          AND b.{createdAt} <= prev.{createdAt})::int AS version
                FROM audit_log cur
                JOIN LATERAL (
                    SELECT {entityId}, {entityType}, {snapshotData}, {createdAt}
                    FROM audit_log
                    WHERE {entityType} = :entityType
                      AND {entityId} = cur.{entityId}
                      AND {createdAt} < cur.{createdAt}
                    ORDER BY {createdAt} DESC LIMIT 1
                ) prev ON true
                WHERE cur.{id} = :snapshotId AND cur.{entityType} = :entityType
                """,
                "snapshotData", SNAPSHOT_DATA.columnName(),
                "entityType",   ENTITY_TYPE.columnName(),
                "entityId",     ENTITY_ID.columnName(),
                "createdAt",    CREATED_AT.columnName(),
                "id",           ID.columnName());

        public static MapSqlParameterSource snapshotByIdParams(Long id, EntityType entityType) {
            return Params.with(ID.columnName(), id).add("entityType", entityType.name());
        }

        public static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
            return Params.with("entityType", entityType.name()).add("entityId", entityId);
        }

        public static MapSqlParameterSource idParams(Long id) {
            return Params.of(ID.columnName(), id);
        }

        public static MapSqlParameterSource previousSnapshotContentParams(Long snapshotId, EntityType entityType) {
            return Params.with("snapshotId", snapshotId).add("entityType", entityType.name());
        }

        public static SnapshotContent mapSnapshotContent(ResultSet rs) throws SQLException {
            return new SnapshotContent(
                    new SnapshotPayload(SNAPSHOT_DATA.extract(rs)),
                    History.VERSION.extract(rs));
        }

        private static abstract class JsonProjection<T> extends SqlFixedQuery<T> {
            protected final ObjectMapper objectMapper;

            protected JsonProjection(List<SqlDescriptorField<?>> fields, ObjectMapper objectMapper) {
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

        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class Activity {

            public static final SqlDescriptorField<Long>    SNAPSHOT_ID   = longVal(ALIAS + ".id", "snapshot_id");
            public static final SqlDescriptorField<Boolean> ENTITY_EXISTS = boolCol("entity_exists");

            public static final SqlCommand QUERY = SqlCommand.of("""
                    SELECT {snapshotIdExpr}           AS {snapshotIdAlias},
                           {entityIdExpr}             AS {entityIdAlias},
                           {entityTypeExpr}           AS {entityTypeAlias},
                           {actionTypeExpr},
                           {createdAtExpr},
                           FALSE                      AS {entityExists},
                           {changesSummaryExpr}::text AS {changesSummaryAlias},
                           {actorIdExpr},
                           NULL::text                 AS {changedByName},
                           {snapshotDataExpr}::text   AS {snapshotDataAlias}
                    FROM {table} {alias}
                    WHERE {actorIdExpr} = :actorId
                    ORDER BY {createdAtExpr} DESC
                    LIMIT 20
                    """,
                    "snapshotIdExpr",      SNAPSHOT_ID.sqlExpression(),
                    "snapshotIdAlias",     SNAPSHOT_ID.alias(),
                    "entityIdExpr",        ENTITY_ID.sqlExpression(),
                    "entityIdAlias",       ENTITY_ID.alias(),
                    "entityTypeExpr",      ENTITY_TYPE.sqlExpression(),
                    "entityTypeAlias",     ENTITY_TYPE.alias(),
                    "actionTypeExpr",      ACTION_TYPE.sqlExpression(),
                    "createdAtExpr",       CREATED_AT.sqlExpression(),
                    "entityExists",        ENTITY_EXISTS.alias(),
                    "changesSummaryExpr",  CHANGES_SUMMARY.sqlExpression(),
                    "changesSummaryAlias", CHANGES_SUMMARY.alias(),
                    "actorIdExpr",         ACTOR_ID.sqlExpression(),
                    "changedByName",       CHANGED_BY_NAME.alias(),
                    "snapshotDataExpr",    SNAPSHOT_DATA.sqlExpression(),
                    "snapshotDataAlias",   SNAPSHOT_DATA.alias(),
                    "table",               TABLE,
                    "alias",               ALIAS);

            public static final List<SqlDescriptorField<?>> FIELDS = List.of(
                    SNAPSHOT_ID, ENTITY_ID, ENTITY_TYPE, ACTION_TYPE,
                    CREATED_AT, ENTITY_EXISTS, CHANGES_SUMMARY, ACTOR_ID, CHANGED_BY_NAME, SNAPSHOT_DATA);

            public static MapSqlParameterSource byActorParams(Long actorId) {
                return Params.of("actorId", actorId);
            }

            public static final class Projection extends JsonProjection<ActivityItemDto> {

                private final List<EntityDisplayNameResolver> resolvers;

                public Projection(ObjectMapper objectMapper, List<EntityDisplayNameResolver> resolvers) {
                    super(FIELDS, objectMapper);
                    this.resolvers = resolvers;
                }

                @Override
                public String querySql() {
                    return QUERY.sql();
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

        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class History {

            public static final SqlDescriptorField<Integer> VERSION            = intCol(ALIAS, "version");
            public static final SqlDescriptorField<Long>    PREV_ID            = longCol(ALIAS, "prev_id");
            public static final SqlDescriptorField<String>  PREV_SNAPSHOT_DATA = strCol(ALIAS, "prev_snapshot_data");

            public static final SqlCommand QUERY = SqlCommand.of("""
                    WITH numbered AS (
                        SELECT {id},
                               ROW_NUMBER()              OVER (PARTITION BY {entityType}, {entityId} ORDER BY {createdAt})         AS {version},
                               LAG({id})                 OVER (PARTITION BY {entityType}, {entityId} ORDER BY {createdAt})         AS {prevId},
                               LAG({snapshotData}::text) OVER (PARTITION BY {entityType}, {entityId} ORDER BY {createdAt})         AS {prevSnapshotData},
                               {snapshotData}::text                                                                                AS {snapshotData},
                               {actionType},
                               {changesSummary}::text                                                                              AS {changesSummary},
                               {actorId},
                               {createdAt}
                        FROM {table}
                        WHERE {entityType} = :entityType AND {entityId} = :entityId
                    )
                    SELECT {alId}, {alVersion}::int, {alActionType},
                           {alActorId},
                           NULL::text AS {changedByName},
                           {alCreatedAt},
                           {alSnapshotData},
                           {alChangesSummary},
                           {alPrevId},
                           {alPrevSnapshotData}
                    FROM numbered {alias}
                    WHERE CAST(:filterUserId AS BIGINT) IS NULL OR {alActorId} = :filterUserId
                    ORDER BY {alVersion} DESC
                    LIMIT 100
                    """,
                    "id",                 ID.columnName(),
                    "entityType",         ENTITY_TYPE.columnName(),
                    "entityId",           ENTITY_ID.columnName(),
                    "createdAt",          CREATED_AT.columnName(),
                    "version",            VERSION.columnName(),
                    "prevId",             PREV_ID.columnName(),
                    "snapshotData",       SNAPSHOT_DATA.columnName(),
                    "prevSnapshotData",   PREV_SNAPSHOT_DATA.columnName(),
                    "actionType",         ACTION_TYPE.columnName(),
                    "changesSummary",     CHANGES_SUMMARY.columnName(),
                    "actorId",            ACTOR_ID.columnName(),
                    "table",              TABLE,
                    "alId",               ID.sqlExpression(),
                    "alVersion",          VERSION.sqlExpression(),
                    "alActionType",       ACTION_TYPE.sqlExpression(),
                    "alActorId",          ACTOR_ID.sqlExpression(),
                    "changedByName",      CHANGED_BY_NAME.alias(),
                    "alCreatedAt",        CREATED_AT.sqlExpression(),
                    "alSnapshotData",     SNAPSHOT_DATA.sqlExpression(),
                    "alChangesSummary",   CHANGES_SUMMARY.sqlExpression(),
                    "alPrevId",           PREV_ID.sqlExpression(),
                    "alPrevSnapshotData", PREV_SNAPSHOT_DATA.sqlExpression(),
                    "alias",              ALIAS);

            public static final List<SqlDescriptorField<?>> FIELDS = List.of(
                    ID, VERSION, ACTION_TYPE, ACTOR_ID, CHANGED_BY_NAME, CREATED_AT,
                    SNAPSHOT_DATA, CHANGES_SUMMARY, PREV_ID, PREV_SNAPSHOT_DATA);

            public static MapSqlParameterSource params(EntityType entityType, Long entityId, Long filterUserId) {
                return Params.with("entityType",   entityType.name())
                                .add("entityId",     entityId)
                                .add("filterUserId", filterUserId);
            }

            public static final class Projection extends JsonProjection<EntityHistoryDto> {

                public Projection(ObjectMapper objectMapper) {
                    super(FIELDS, objectMapper);
                }

                @Override
                public String querySql() {
                    return QUERY.sql();
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

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Write {

        public record InsertEntry(EntityType entityType, Long entityId, ActionType actionType,
                                  String snapshotData, String changesSummary, Long actorId) {}

        public static final SqlCommand INSERT = SqlCommand.of(
                "INSERT INTO {table} ({entityType}, {entityId}, {actionType}, {snapshotData}, {changesSummary}, {actorId})" +
                " VALUES (:entityType, :entityId, :actionType, CAST(:snapshot_data AS JSONB), CAST(:changes_summary AS JSONB), :actorId)",
                "table",          TABLE,
                "entityType",     ENTITY_TYPE.columnName(),
                "entityId",       ENTITY_ID.columnName(),
                "actionType",     ACTION_TYPE.columnName(),
                "snapshotData",   SNAPSHOT_DATA.columnName(),
                "changesSummary", CHANGES_SUMMARY.columnName(),
                "actorId",        ACTOR_ID.columnName());

        public static final SqlCommand UPDATE_CHANGES_SUMMARY = SqlCommand.of(
                "UPDATE {table} SET {changesSummary} = CAST(:changesSummary AS JSONB) WHERE {id} = :id",
                "table",          TABLE,
                "changesSummary", CHANGES_SUMMARY.columnName(),
                "id",             ID.columnName());

        public static final SqlCommand DELETE_OLDER_THAN = SqlCommand.of(
                "DELETE FROM {table} WHERE {createdAt} < NOW() - MAKE_INTERVAL(days => :days)",
                "table",     TABLE,
                "createdAt", CREATED_AT.columnName());

        public static MapSqlParameterSource insertParams(InsertEntry e) {
            return Params.with("entityType",      e.entityType().name())
                            .add("entityId",        e.entityId())
                            .add("actionType",      e.actionType().name())
                            .add("snapshot_data",   e.snapshotData())
                            .add("changes_summary", e.changesSummary())
                            .add("actorId",         e.actorId());
        }

        public static MapSqlParameterSource updateChangesSummaryParams(Long snapshotId, String changesSummary) {
            return Params.with(ID.columnName(), snapshotId).add("changesSummary", changesSummary);
        }

        public static MapSqlParameterSource deleteOlderThanParams(int days) {
            return Params.of("days", days);
        }
    }

}
