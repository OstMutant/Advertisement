package org.ost.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

/** Stateless chain for {@code /mcp}: no CSRF protection and every request permitted (the REST API checks the forwarded key), except a browser {@code Origin} outside the configured list, which gets 403. */
@Configuration
public class McpSecurityConfig {

    @Bean
    @Order(3)
    @SuppressWarnings({"java:S112", "java:S1130"})
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, McpProperties properties) throws Exception {
        http
                .securityMatcher("/mcp")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new McpOriginFilter(properties.allowedOrigins()), CsrfFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
