package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.spi.UiLabelHook;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserActorNameService {

    private final ActorLookupService actorLookupService;
    private final UiLabelHook        uiLabelHook;

    public Map<Long, String> resolveNames(@NonNull Set<Long> actorIds) {
        Map<Long, String> names = new HashMap<>(actorLookupService.findActorNames(actorIds));
        actorLookupService.findDeletedIds(actorIds).forEach(id ->
                names.computeIfPresent(id, (_, name) -> uiLabelHook.translateActorDeletedSuffix(name)));
        return names;
    }
}
