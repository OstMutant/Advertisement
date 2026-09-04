package org.ost.restapi.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Stateless security chain for the external REST API ({@code /api/**}), coexisting with the
 * Vaadin app's own security chain via Spring Security's ordered multi-chain matching (see
 * {@code .claude/nav/adr-index.md}). Reads are public; writes need HTTP Basic (key issuance only)
 * or a bearer API key, resolved by {@link ApiKeyAuthenticationFilter}.
 */
@Configuration
@RequiredArgsConstructor
public class ApiSecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    @Order(1)
    @SuppressWarnings({"java:S112", "java:S1130"})
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/advertisements", "/api/advertisements/**",
                                "/api/provider-profiles", "/api/provider-profiles/**",
                                "/api/taxons", "/api/taxons/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .addFilterAfter(apiKeyAuthenticationFilter, BasicAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
