package org.ost.advertisement.services.audit;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.AuditUserProvider;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.auth.AuthContextService;
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
