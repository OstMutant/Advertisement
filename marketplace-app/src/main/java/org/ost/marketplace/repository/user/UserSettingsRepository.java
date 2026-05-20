package org.ost.marketplace.repository.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.exceptions.persistence.SettingsPersistenceException;
import org.ost.sqlengine.RepositoryCustom;
import org.ost.sqlengine.exec.SqlCommand;
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

    private static final SqlCommand SAVE_SETTINGS = SqlCommand.of(
            "UPDATE " + UserDescriptor.Write.TABLE +
            " SET "   + UserDescriptor.Write.SETTINGS + " = :settings::jsonb" +
            " WHERE id = :userId"
    );

    private static final SqlCommand SELECT_SETTINGS = SqlCommand.of(
            "SELECT " + UserDescriptor.Write.SETTINGS +
            " FROM "  + UserDescriptor.Write.TABLE +
            " WHERE id = :userId"
    );

    private final RepositoryCustom repo;
    private final ObjectMapper     mapper;

    public UserSettingsRepository(JdbcClient jdbcClient,
                                  @Qualifier("userSettingsObjectMapper") ObjectMapper mapper) {
        this.repo   = new RepositoryCustom(jdbcClient);
        this.mapper = mapper;
    }

    @Transactional
    public void save(Long userId, UserSettings settings) {
        try {
            repo.execute(SAVE_SETTINGS,
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
            return repo.findOne(SELECT_SETTINGS,
                           new MapSqlParameterSource("userId", userId),
                           String.class)
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
