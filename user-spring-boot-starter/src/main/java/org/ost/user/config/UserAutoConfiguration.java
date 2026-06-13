package org.ost.user.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.spi.UserPort;
import org.ost.platform.user.spi.UserSettingsChangedHook;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.user.spi", "org.ost.user.services", "org.ost.user.repository"})
@EnableJdbcRepositories(basePackages = "org.ost.user.repository")
public class UserAutoConfiguration {

    @Bean("userSettingsObjectMapper")
    @ConditionalOnMissingBean(name = "userSettingsObjectMapper")
    ObjectMapper userSettingsObjectMapper() {
        return new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<UserPort> userPortFactory(ObjectProvider<UserPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<UserSettingsChangedHook> userSettingsChangedHookFactory(ObjectProvider<UserSettingsChangedHook> p) {
        return new ComponentFactory<>(p);
    }
}
