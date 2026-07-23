package org.ost.marketplace.services.user;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.spi.UserPort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.ost.marketplace.services.i18n.I18nKey.AUDIT_ACTOR_DELETED_NAME;

@Service
@RequiredArgsConstructor
public class UserActorNameService {

    private final ComponentFactory<UserPort> userPortFactory;
    private final I18nService                i18n;

    public Map<Long, String> resolveNames(@NonNull Set<Long> actorIds) {
        return userPortFactory.findIfAvailable().map(port -> {
            Map<Long, String> names = new HashMap<>(port.findActorNames(actorIds));
            port.findDeletedIds(actorIds).forEach(id ->
                    names.computeIfPresent(id, (_, name) -> i18n.get(AUDIT_ACTOR_DELETED_NAME, name)));
            return names;
        }).orElse(Map.of());
    }
}
