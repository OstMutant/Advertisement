package org.ost.advertisement.configuration.db;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

@Configuration
@EnableJdbcAuditing(auditorAwareRef = "auditorProvider")
public class AuditingConfig {

	@Bean
	public AuditorAware<Long> auditorProvider() {
		return new SecurityAuditorAware();
	}
}
