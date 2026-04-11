package org.ost.advertisement.repository.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.ost.advertisement.dto.UserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class UserSettingsRepository {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsRepository.class);

    private final NamedParameterJdbcTemplate jdbc;
    @Qualifier("userSettingsObjectMapper") private final ObjectMapper mapper;

    @Transactional
    public void save(Long userId, UserSettings settings) {
        try {
            jdbc.update(
                    "UPDATE user_information SET settings = :settings::jsonb WHERE id = :userId",
                    new MapSqlParameterSource()
                            .addValue("settings", mapper.writeValueAsString(settings))
                            .addValue("userId",   userId)
            );
        } catch (Exception ex) {
            log.error("Failed to save settings for userId={}", userId, ex);
            throw new RuntimeException("Failed to save settings", ex);
        }
    }

    public UserSettings load(Long userId) {
        try {
            String json = jdbc.queryForObject(
                    "SELECT settings FROM user_information WHERE id = :userId",
                    new MapSqlParameterSource("userId", userId),
                    String.class
            );
            if (json == null) {
                log.debug("settings IS NULL for userId={}, using defaults", userId);
                return UserSettings.defaultSettings();
            }
            return mapper.readValue(json, UserSettings.class);
        } catch (Exception ex) {
            log.warn("Failed to load settings for userId={}, using defaults", userId, ex);
            return UserSettings.defaultSettings();
        }
    }
}
