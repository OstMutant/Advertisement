package org.ost.advertisement.configuration.db;

import java.util.Optional;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.springframework.data.domain.AuditorAware;

public class SecurityAuditorAware implements AuditorAware<Long> {

	@Override
	public Optional<Long> getCurrentAuditor() {
		return AuthUtil.getOptionalCurrentUser().map(User::getId);
	}
}
