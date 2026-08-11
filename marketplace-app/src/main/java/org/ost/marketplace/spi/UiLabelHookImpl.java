package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.orchestrator.spi.UiLabelHook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UiLabelHookImpl implements UiLabelHook {

    private final I18nService i18nService;

    @Override
    public String translateActorDeletedSuffix(@NonNull String actorName) {
        return i18nService.get(I18nKey.AUDIT_ACTOR_DELETED_NAME, actorName);
    }
}
