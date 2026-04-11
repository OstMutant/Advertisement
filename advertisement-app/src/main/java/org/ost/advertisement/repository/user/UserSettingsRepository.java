package org.ost.advertisement.repository.user;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
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

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public void updatePageSizes(Long userId, int adsPageSize, int usersPageSize) {
        try {
            jdbc.update(
                    "UPDATE user_information SET settings = settings || jsonb_build_object('adsPageSize', :adsPageSize::int, 'usersPageSize', :usersPageSize::int) WHERE id = :userId",
                    new MapSqlParameterSource()
                            .addValue("adsPageSize",   adsPageSize)
                            .addValue("usersPageSize", usersPageSize)
                            .addValue("userId",        userId)
            );
        } catch (Exception ex) {
            log.error("Failed to update page sizes for userId={}", userId, ex);
            throw new RuntimeException("Failed to update page sizes", ex);
        }
    }

    public UserSettings loadSettings(Long userId) {
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
            return MAPPER.readValue(json, UserSettings.class);
        } catch (Exception ex) {
            log.warn("Failed to load settings for userId={}, using defaults", userId, ex);
            return UserSettings.defaultSettings();
        }
    }
}
