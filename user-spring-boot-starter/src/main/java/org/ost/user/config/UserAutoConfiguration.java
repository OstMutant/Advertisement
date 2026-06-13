package org.ost.user.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
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

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.user.spi", "org.ost.user.services", "org.ost.user.repository"})
@EnableJdbcRepositories(basePackages = "org.ost.user.repository")
public class UserAutoConfiguration {

    @Bean("userLiquibase")
    @ConditionalOnMissingBean(name = "userLiquibase")
    public SpringLiquibase userLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/user-changelog/user-changelog-master.xml");
        return liq;
    }

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
