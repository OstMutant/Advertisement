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

@Slf4j
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class UserSettingsRepository {

    private final JdbcClient jdbcClient;
    @Qualifier("userSettingsObjectMapper")
    private final ObjectMapper mapper;

    @Transactional
    public void save(@NonNull Long userId, @NonNull UserSettingsDto settings) {
        UserSettingsDto toStore = settings.toBuilder().version(settings.getVersion() + 1).build();
        String json;
        try {
            json = mapper.writeValueAsString(toStore);
        } catch (Exception ex) {
            log.error("Failed to serialize settings for userId={}", userId, ex);
            throw new RuntimeException("Failed to serialize settings for userId=" + userId, ex);
        }
        int updated = jdbcClient.sql("""
                        UPDATE user_information SET settings = :settings::jsonb
                        WHERE id = :userId AND (settings->>'version')::bigint = :expectedVersion
                        """)
                .paramSource(new MapSqlParameterSource()
                        .addValue("settings",        json)
                        .addValue("userId",          userId)
                        .addValue("expectedVersion", settings.getVersion()))
                .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Settings for userId=" + userId + " were modified concurrently");
        }
    }

    public UserSettingsDto load(@NonNull Long userId) {
        try {
            return jdbcClient.sql("SELECT settings FROM user_information WHERE id = :userId")
                             .paramSource(new MapSqlParameterSource("userId", userId))
                             .query(String.class)
                             .optional()
                             .map(json -> {
                                 try { return mapper.readValue(json, UserSettingsDto.class); }
                                 catch (Exception e) { throw new RuntimeException(e); }
                             })
                             .orElseGet(() -> {
                                 log.debug("settings IS NULL for userId={}, using defaults", userId);
                                 return UserSettingsDto.defaultSettings();
                             });
        } catch (Exception ex) {
            log.warn("Failed to load settings for userId={}, using defaults", userId, ex);
            return UserSettingsDto.defaultSettings();
        }
    }
}
