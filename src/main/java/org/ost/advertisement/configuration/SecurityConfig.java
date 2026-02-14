package org.ost.advertisement.configuration;

import com.vaadin.flow.server.HandlerHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    private static final String SERVLET_MAPPING_PATH = "/";

    private final RequestMatcher vaadinInternalRequestMatcher = this::isVaadinInternalRequest;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(vaadinInternalRequestMatcher).permitAll()
                        .anyRequest().permitAll()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers(vaadinInternalRequestMatcher))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    private boolean isVaadinInternalRequest(HttpServletRequest request) {
        return HandlerHelper.isFrameworkInternalRequest(SERVLET_MAPPING_PATH, request);
    }
}
