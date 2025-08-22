package org.ost.advertisement.configuration;

import com.vaadin.flow.server.HandlerHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	private static final String SERVLET_MAPPING_PATH = "/";

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(this::isVaadinInternalRequest).permitAll()
				.anyRequest().permitAll()
			)
			.csrf(csrf -> csrf
				.ignoringRequestMatchers(this::isVaadinInternalRequest)
			)
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
			)
			.httpBasic(Customizer.withDefaults())
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable);

		return http.build();
	}

	private boolean isVaadinInternalRequest(HttpServletRequest request) {
		return HandlerHelper.isFrameworkInternalRequest(SERVLET_MAPPING_PATH, request);
	}
}
