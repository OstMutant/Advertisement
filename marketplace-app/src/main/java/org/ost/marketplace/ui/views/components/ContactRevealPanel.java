package org.ost.marketplace.ui.views.components;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.ContactAccessService;
import org.ost.orchestrator.services.ContactRevealService;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.TooManyAttemptsException;
import org.ost.platform.core.model.EntityRef;
import org.springframework.context.annotation.Scope;

import java.util.Optional;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** Reveals contact details (phone in-place, Telegram/Viber via deep link) and records view counts. */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ContactRevealPanel extends Div
        implements Configurable<ContactRevealPanel, ContactRevealPanel.Parameters>, Initialization<ContactRevealPanel>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull EntityRef entityRef;
    }

    private final ContactAccessService     contactAccessService;
    private final ContactRevealService    contactRevealService;
    private final HttpServletRequest       request;
    private final AccessEvaluator          access;
    private final NotificationService      notificationService;
    @Getter
    private final I18nService              i18nService;

    @Override
    @PostConstruct
    public ContactRevealPanel init() {
        addClassName("contact-reveal-panel");
        return this;
    }

    @Override
    public ContactRevealPanel configure(Parameters p) {
        removeAll();
        setVisible(false);

        EntityRef entityRef = p.getEntityRef();
        ContactInfoDto contact = contactAccessService.resolveContact(entityRef.entityType(), entityRef.entityId()).orElse(null);
        if (contact == null) return this;

        for (var entry : contact.presentChannels()) {
            switch (entry.getKey()) {
                case PHONE -> add(buildPhoneRow(entityRef));
                case TELEGRAM -> add(buildDeepLinkRow(entityRef, "contact-reveal-telegram", VaadinIcon.PAPERPLANE,
                        getValue(CONTACT_REVEAL_BUTTON_TELEGRAM), ContactChannel.TELEGRAM));
                case VIBER -> add(buildDeepLinkRow(entityRef, "contact-reveal-viber", VaadinIcon.MOBILE,
                        getValue(CONTACT_REVEAL_BUTTON_VIBER), ContactChannel.VIBER));
            }
        }

        setVisible(getComponentCount() > 0);
        return this;
    }

    private Div buildPhoneRow(EntityRef entityRef) {
        Div row = new Div();
        row.addClassName("contact-reveal-row");
        row.addClassName("contact-reveal-phone");

        var button = new Button(getValue(CONTACT_REVEAL_BUTTON_SHOW_PHONE), VaadinIcon.PHONE.create());
        button.addClickListener(_ -> reveal(entityRef, ContactChannel.PHONE).ifPresent(revealed -> {
            Span value = new Span(revealed.value());
            value.addClassName("contact-reveal-phone-value");
            row.removeAll();
            row.add(value);
        }));
        row.add(button);
        return row;
    }

    private Div buildDeepLinkRow(EntityRef entityRef, String cssClass, VaadinIcon icon, String label, ContactChannel channel) {
        Div row = new Div();
        row.addClassName("contact-reveal-row");
        row.addClassName(cssClass);

        var button = new Button(label, icon.create());
        button.addClickListener(_ -> reveal(entityRef, channel)
                .ifPresent(revealed -> UI.getCurrent().getPage().open(revealed.deepLink(), "_blank")));
        row.add(button);
        return row;
    }

    private Optional<ContactRevealService.RevealedContact> reveal(EntityRef entityRef, ContactChannel channel) {
        try {
            return contactRevealService.reveal(entityRef.entityType(), entityRef.entityId(), channel,
                    access.getCurrentUserId(), request.getRemoteAddr());
        } catch (TooManyAttemptsException _) {
            notificationService.error(CONTACT_REVEAL_NOTIFICATION_RATE_LIMITED);
            return Optional.empty();
        }
    }
}
