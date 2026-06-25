package org.ost.marketplace.config.db;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.platform.user.dto.UserDto;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import java.util.Optional;

@Configuration
@EnableJdbcAuditing
@EnableJdbcRepositories(basePackages = "org.ost.marketplace.repository")
@RequiredArgsConstructor
public class JdbcAuditingConfig implements AuditorAware<Long> {

    private final AuthContextService authContextService;

    @Override
    public @NonNull Optional<Long> getCurrentAuditor() {
        return authContextService.getCurrentUser().map(UserDto::id);
    }
}
