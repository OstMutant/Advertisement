package org.ost.user.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.user.dto.UserSettingsDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class UserPreferencesRepository {

    private final JdbcClient jdbcClient;
    @Qualifier("userSettingsObjectMapper")
    private final ObjectMapper mapper;

    public void insertDefault(@NonNull Long actorId) {
        String json = writeSettings(actorId, UserSettingsDto.defaultSettings());
        jdbcClient.sql("INSERT INTO user_preferences (actor_id, settings) VALUES (:actorId, :settings::jsonb)")
                  .paramSource(new MapSqlParameterSource()
                          .addValue("actorId",  actorId)
                          .addValue("settings", json))
                  .update();
    }

    public String findLocaleByActorId(@NonNull Long actorId) {
        return findLocalesByActorIds(Set.of(actorId)).get(actorId);
    }

    public Map<Long, String> findLocalesByActorIds(@NonNull Set<Long> actorIds) {
        // Collectors.toMap() rejects null values (locale is null until a user sets one) -- accumulate into a HashMap instead.
        Map<Long, String> result = new HashMap<>();
        for (LocaleRow row : jdbcClient.sql("SELECT actor_id, locale FROM user_preferences WHERE actor_id = ANY(:actorIds)")
                .paramSource(new MapSqlParameterSource("actorIds", actorIds.toArray(new Long[0])))
                .query((rs, _) -> new LocaleRow(rs.getObject("actor_id", Long.class), rs.getString("locale")))
                .list()) {
            result.put(row.actorId(), row.locale());
        }
        return result;
    }

    private record LocaleRow(Long actorId, String locale) {}

    public void updateLocale(@NonNull Long actorId, @NonNull String locale) {
        int updated = jdbcClient.sql("UPDATE user_preferences SET locale = :locale WHERE actor_id = :actorId")
                  .paramSource(new MapSqlParameterSource()
                          .addValue("locale",  locale)
                          .addValue("actorId", actorId))
                  .update();
        if (updated == 0) {
            throw new IllegalStateException("No user_preferences row for actorId=" + actorId);
        }
    }

    public void deleteByActorId(@NonNull Long actorId) {
        jdbcClient.sql("DELETE FROM user_preferences WHERE actor_id = :actorId")
                  .paramSource(new MapSqlParameterSource("actorId", actorId))
                  .update();
    }

    @Transactional
    public void saveSettings(@NonNull Long actorId, @NonNull UserSettingsDto settings) {
        UserSettingsDto toStore = settings.toBuilder().version(settings.getVersion() + 1).build();
        String json = writeSettings(actorId, toStore);
        int updated = jdbcClient.sql("""
                        UPDATE user_preferences SET settings = :settings::jsonb
                        WHERE actor_id = :actorId AND (settings->>'version')::bigint = :expectedVersion
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("settings",        json)
                        .addValue("actorId",         actorId)
                        .addValue("expectedVersion", settings.getVersion()))
                .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Settings for actorId=" + actorId + " were modified concurrently");
        }
    }

    public UserSettingsDto loadSettings(@NonNull Long actorId) {
        try {
            return jdbcClient.sql("SELECT settings FROM user_preferences WHERE actor_id = :actorId")
                             .paramSource(new MapSqlParameterSource("actorId", actorId))
                             .query(String.class)
                             .optional()
                             .map(json -> readSettings(actorId, json))
                             .orElseGet(() -> {
                                 log.debug("settings IS NULL for actorId={}, using defaults", actorId);
                                 return UserSettingsDto.defaultSettings();
                             });
        } catch (Exception ex) {
            log.warn("Failed to load settings for actorId={}, using defaults", actorId, ex);
            return UserSettingsDto.defaultSettings();
        }
    }

    private String writeSettings(Long actorId, UserSettingsDto settings) {
        try {
            return mapper.writeValueAsString(settings);
        } catch (Exception ex) {
            log.error("Failed to serialize settings for actorId={}", actorId, ex);
            throw new RuntimeException("Failed to serialize settings for actorId=" + actorId, ex);
        }
    }

    private UserSettingsDto readSettings(Long actorId, String json) {
        try {
            UserSettingsDto settings = mapper.readValue(json, UserSettingsDto.class);
            if (settings.getSchemaVersion() != UserSettingsDto.SCHEMA_VERSION) {
                log.warn("Settings schema version mismatch for actorId={}: stored={}, expected={}",
                        actorId, settings.getSchemaVersion(), UserSettingsDto.SCHEMA_VERSION);
            }
            return settings;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
