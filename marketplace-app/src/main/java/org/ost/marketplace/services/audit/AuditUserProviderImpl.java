package org.ost.marketplace.services.audit;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditUserProvider;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.services.auth.AuthContextService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuditUserProviderImpl implements AuditUserProvider {

    private final AuthContextService authContextService;

    @Override
    public Optional<Long> getCurrentUserId() {
        return authContextService.getCurrentUser().map(User::getId);
    }
}
