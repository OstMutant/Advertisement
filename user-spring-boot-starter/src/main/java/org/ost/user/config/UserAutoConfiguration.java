package org.ost.user.config;

import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.spi.UserAccountPort;
import org.ost.platform.user.spi.UserAuthorizationPort;
import org.ost.platform.user.spi.UserPort;
import org.ost.platform.user.spi.UserPreferencesPort;
import org.ost.platform.user.spi.UserSettingsChangedHook;
import org.ost.user.services.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;

/**
 * Auto-configures the User domain -- Liquibase migration, Spring Security beans (password
 * encoder, {@code UserDetailsService}, {@code AuthenticationManager}), and every
 * {@code User*Port} component-factory bean.
 */
@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.user.spi", "org.ost.user.services", "org.ost.user.repository", "org.ost.user.security"})
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
        return JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
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
                .map(userService::toPrincipal)
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
    public ComponentFactory<UserAccountPort> userAccountPortFactory(ObjectProvider<UserAccountPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<UserAuthorizationPort> userAuthorizationPortFactory(ObjectProvider<UserAuthorizationPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<UserPreferencesPort> userPreferencesPortFactory(ObjectProvider<UserPreferencesPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<UserSettingsChangedHook> userSettingsChangedHookFactory(ObjectProvider<UserSettingsChangedHook> p) {
        return new ComponentFactory<>(p);
    }
}
