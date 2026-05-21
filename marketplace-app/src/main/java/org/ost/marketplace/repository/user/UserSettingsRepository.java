package org.ost.marketplace.repository.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.exceptions.persistence.SettingsPersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserSettingsRepository {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsRepository.class);

    private static final String TABLE = "user_information";
    private static final String SAVE_SETTINGS =
            "UPDATE " + TABLE + " SET settings = :settings::jsonb WHERE id = :userId";
    private static final String LOAD_SETTINGS =
            "SELECT settings FROM " + TABLE + " WHERE id = :userId";

    private final JdbcClient jdbcClient;
    private final ObjectMapper mapper;

    public UserSettingsRepository(JdbcClient jdbcClient,
                                  @Qualifier("userSettingsObjectMapper") ObjectMapper mapper) {
        this.jdbcClient = jdbcClient;
        this.mapper = mapper;
    }

    @Transactional
    public void save(Long userId, UserSettings settings) {
        try {
            jdbcClient.sql(SAVE_SETTINGS)
                      .paramSource(new MapSqlParameterSource()
                              .addValue("settings", mapper.writeValueAsString(settings))
                              .addValue("userId",   userId))
                      .update();
        } catch (Exception ex) {
            log.error("Failed to save settings for userId={}", userId, ex);
            throw new SettingsPersistenceException("Failed to save settings for userId=" + userId, ex);
        }
    }

    public UserSettings load(Long userId) {
        try {
            return jdbcClient.sql(LOAD_SETTINGS)
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
