package org.ost.advertisement.audit.ui;

import com.vaadin.flow.server.VaadinSession;
import org.ost.advertisement.events.model.ActionType;
import org.springframework.context.MessageSource;

import java.util.Locale;

public interface AuditI18nSupport {

    MessageSource getMessageSource();

    default String msg(String key) {
        VaadinSession session = VaadinSession.getCurrent();
        Locale locale = (session != null && session.getLocale() != null)
                ? session.getLocale() : Locale.getDefault();
        return getMessageSource().getMessage(key, null, locale);
    }

    default String formatAction(ActionType actionType) {
        return switch (actionType) {
            case CREATED -> msg(AuditKeys.ACTIVITY_ACTION_CREATED);
            case UPDATED -> msg(AuditKeys.ACTIVITY_ACTION_UPDATED);
            case DELETED -> msg(AuditKeys.ACTIVITY_ACTION_DELETED);
        };
    }
}
