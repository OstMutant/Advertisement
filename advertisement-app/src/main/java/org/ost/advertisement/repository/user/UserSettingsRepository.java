package org.ost.advertisement.repository.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.exceptions.persistence.SettingsPersistenceException;
import org.ost.advertisement.repository.user.UserProjection;
import org.ost.sqlengine.writer.SqlFixedWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class UserSettingsRepository {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsRepository.class);

    private static final SqlFixedWriter SAVE_SETTINGS = SqlFixedWriter.of(
            "UPDATE " + UserProjection.Write.TABLE +
            " SET " + UserProjection.Write.SETTINGS + " = :settings::jsonb WHERE id = :userId"
    );

    private final JdbcClient jdbcClient;
    @Qualifier("userSettingsObjectMapper") private final ObjectMapper mapper;

    @Transactional
    public void save(Long userId, UserSettings settings) {
        try {
            SAVE_SETTINGS.execute(jdbcClient,
                    new MapSqlParameterSource()
                            .addValue("settings", mapper.writeValueAsString(settings))
                            .addValue("userId",   userId));
        } catch (Exception ex) {
            log.error("Failed to save settings for userId={}", userId, ex);
            throw new SettingsPersistenceException("Failed to save settings for userId=" + userId, ex);
        }
    }

    public UserSettings load(Long userId) {
        try {
            String json = jdbcClient.sql(
                    "SELECT " + UserProjection.Write.SETTINGS +
                    " FROM " + UserProjection.Write.TABLE + " WHERE id = :userId")
                    .paramSource(new MapSqlParameterSource("userId", userId))
                    .query(String.class).optional().orElse(null);
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
