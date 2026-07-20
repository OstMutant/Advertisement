package org.ost.user.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.config.CleanupProperties;
import org.ost.platform.user.spi.UserPort;
import org.ost.platform.user.spi.UserSettingsChangedHook;
import org.ost.user.security.UserPrincipal;
import org.ost.user.services.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import javax.sql.DataSource;
import java.util.TimeZone;

@Slf4j
@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.user.spi", "org.ost.user.services", "org.ost.user.repository", "org.ost.user.security"})
@EnableJdbcRepositories(basePackages = "org.ost.user.repository")
@EnableConfigurationProperties(CleanupProperties.class)
@EnableScheduling
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
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public UserDetailsService userDetailsService(UserService userService) {
        return email -> userService.findByEmail(email)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
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

    @Bean
    SchedulingConfigurer userCleanupScheduler(UserService userService, CleanupProperties cleanupProperties) {
        return registrar -> registrar.addTriggerTask(
                () -> {
                    log.info("User cleanup started, retention = {} days", cleanupProperties.retentionDays());
                    userService.cleanup(cleanupProperties.retentionDays());
                    log.info("User cleanup finished");
                },
                new CronTrigger(cleanupProperties.cronExpression(),
                                TimeZone.getTimeZone(cleanupProperties.timezone())));
    }
}
