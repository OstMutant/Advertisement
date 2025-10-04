package org.ost.advertisement.configuration;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

@Configuration
@EnableJdbcAuditing(auditorAwareRef = "auditorProvider")
public class AuditingConfig {

	@Bean
	public AuditorAware<String> auditorProvider() {
		return Optional::empty;
	}
}
