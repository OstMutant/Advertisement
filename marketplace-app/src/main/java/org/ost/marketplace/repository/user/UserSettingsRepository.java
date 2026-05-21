package org.ost.marketplace.repository.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.exceptions.persistence.SettingsPersistenceException;
import org.ost.sqlengine.RepositoryCustom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static org.ost.marketplace.repository.user.UserDescriptor.*;

@Repository
public class UserSettingsRepository extends RepositoryCustom {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsRepository.class);

    private final ObjectMapper mapper;

    public UserSettingsRepository(JdbcClient jdbcClient,
                                  @Qualifier("userSettingsObjectMapper") ObjectMapper mapper) {
        super(jdbcClient);
        this.mapper = mapper;
    }

    @Transactional
    public void save(Long userId, UserSettings settings) {
        try {
            executeUpdate(Write.SAVE_SETTINGS,
                    Write.saveSettingsParams(userId, mapper.writeValueAsString(settings)));
        } catch (Exception ex) {
            log.error("Failed to save settings for userId={}", userId, ex);
            throw new SettingsPersistenceException("Failed to save settings for userId=" + userId, ex);
        }
    }

    public UserSettings load(Long userId) {
        try {
            return findOne(Write.SELECT_SETTINGS, Write.loadSettingsParams(userId), String.class)
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
