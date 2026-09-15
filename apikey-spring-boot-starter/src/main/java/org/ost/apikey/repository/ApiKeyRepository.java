package org.ost.apikey.repository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.apikey.entity.ApiKey;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * JdbcClient-backed bespoke queries for {@code api_key} -- the trivial save/find lives in
 * {@link ApiKeyCrudRepository} instead.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ApiKeyRepository {

    private static final String PARAM_ACTOR_ID = "actorId";

    private static final RowMapper<ApiKey> ROW_MAPPER = (rs, _) -> {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp lastUsedAt = rs.getTimestamp("last_used_at");
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return ApiKey.builder()
                .id(rs.getObject("id", Long.class))
                .actorId(rs.getObject("actor_id", Long.class))
                .keyHash(rs.getString("key_hash"))
                .keyPrefix(rs.getString("key_prefix"))
                .label(rs.getString("label"))
                .createdAt(createdAt != null ? createdAt.toInstant() : null)
                .lastUsedAt(lastUsedAt != null ? lastUsedAt.toInstant() : null)
                .revokedAt(revokedAt != null ? revokedAt.toInstant() : null)
                .build();
    };

    private final JdbcClient jdbcClient;
    private final ApiKeyCrudRepository crud;

    public ApiKey save(@NonNull ApiKey apiKey) { return crud.save(apiKey); }

    public Optional<ApiKey> findActiveByKeyHash(@NonNull String keyHash) {
        return jdbcClient.sql("""
                        SELECT id, actor_id, key_hash, key_prefix, label, created_at, last_used_at, revoked_at
                        FROM api_key WHERE key_hash = :keyHash AND revoked_at IS NULL
                        """)
                .paramSource(new MapSqlParameterSource("keyHash", keyHash))
                .query(ROW_MAPPER)
                .optional();
    }

    public List<ApiKey> findByActorId(@NonNull Long actorId) {
        return jdbcClient.sql("""
                        SELECT id, actor_id, key_hash, key_prefix, label, created_at, last_used_at, revoked_at
                        FROM api_key WHERE actor_id = :actorId ORDER BY created_at DESC
                        """)
                .paramSource(new MapSqlParameterSource(PARAM_ACTOR_ID, actorId))
                .query(ROW_MAPPER)
                .list();
    }

    public void revoke(@NonNull Long actorId, @NonNull Long keyId) {
        jdbcClient.sql("UPDATE api_key SET revoked_at = NOW() WHERE id = :id AND actor_id = :actorId AND revoked_at IS NULL")
                  .paramSource(new MapSqlParameterSource()
                          .addValue("id", keyId)
                          .addValue(PARAM_ACTOR_ID, actorId))
                  .update();
    }

    public void deleteAllForActor(@NonNull Long actorId) {
        jdbcClient.sql("DELETE FROM api_key WHERE actor_id = :actorId")
                  .paramSource(new MapSqlParameterSource(PARAM_ACTOR_ID, actorId))
                  .update();
    }

    // Failure here must never break the request the key is authenticating.
    public void touchLastUsed(@NonNull Long id) {
        try {
            jdbcClient.sql("UPDATE api_key SET last_used_at = NOW() WHERE id = :id")
                      .paramSource(new MapSqlParameterSource("id", id))
                      .update();
        } catch (Exception ex) {
            log.warn("Failed to update last_used_at for id={}", id, ex);
        }
    }
}
