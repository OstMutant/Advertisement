package org.ost.marketplace.repository.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.exceptions.persistence.SettingsPersistenceException;
import org.springframework.beans.factory.annotation.Qualifier;
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
    public void save(@NonNull Long userId, @NonNull UserSettings settings) {
        try {
            jdbcClient.sql("UPDATE user_information SET settings = :settings::jsonb WHERE id = :userId")
                      .paramSource(new MapSqlParameterSource()
                              .addValue("settings", mapper.writeValueAsString(settings))
                              .addValue("userId",   userId))
                      .update();
        } catch (Exception ex) {
            log.error("Failed to save settings for userId={}", userId, ex);
            throw new SettingsPersistenceException("Failed to save settings for userId=" + userId, ex);
        }
    }

    public UserSettings load(@NonNull Long userId) {
        try {
            return jdbcClient.sql("SELECT settings FROM user_information WHERE id = :userId")
                             .paramSource(new MapSqlParameterSource("userId", userId))
                             .query(String.class)
                             .optional()
                             .map(json -> {
                                 try { return mapper.readValue(json, UserSettings.class); }
                                 catch (Exception e) { throw new RuntimeException(e); }
                             })
                             .orElseGet(() -> {
                                 log.debug("settings IS NULL for userId={}, using defaults", userId);
                                 return UserSettings.defaultSettings();
                             });
        } catch (Exception ex) {
            log.warn("Failed to load settings for userId={}, using defaults", userId, ex);
            return UserSettings.defaultSettings();
        }
    }
}
