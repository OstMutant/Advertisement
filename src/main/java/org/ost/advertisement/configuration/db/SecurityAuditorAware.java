package org.ost.advertisement.configuration.db;

import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

public class SecurityAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        return AuthUtil.getOptionalCurrentUser().map(User::getId);
    }
}
