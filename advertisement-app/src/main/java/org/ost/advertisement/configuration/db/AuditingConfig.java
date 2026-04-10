package org.ost.advertisement.configuration.db;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.auth.AuthContextService;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

import java.util.Optional;

@Configuration
@EnableJdbcAuditing
@RequiredArgsConstructor
public class AuditingConfig implements AuditorAware<Long> {

    private final AuthContextService authContextService;

    @Override
    public @NonNull Optional<Long> getCurrentAuditor() {
        return authContextService.getCurrentUser().map(User::getId);
    }
}

