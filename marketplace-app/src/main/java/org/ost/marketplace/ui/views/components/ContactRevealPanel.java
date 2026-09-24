package org.ost.marketplace.ui.views.components;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.services.security.ContactRevealRateLimiter;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.ContactAccessService;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.TooManyAttemptsException;
import org.ost.platform.core.model.EntityRef;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** Public contact-reveal card -- phone reveals in place, Telegram/Viber open their deep link immediately, each channel independently recording a {@code contact_view} row. Hidden entirely when the resolved contact has no channel set. */
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
    private final ContactRevealRateLimiter rateLimiter;
    private final AccessEvaluator          access;
    private final NotificationService      notificationService;
    @Getter
    private final I18nService              i18nService;

    private Parameters params;

    @Override
    @PostConstruct
    public ContactRevealPanel init() {
        addClassName("contact-reveal-panel");
        return this;
    }

    @Override
    public ContactRevealPanel configure(Parameters p) {
        this.params = p;
        removeAll();
        setVisible(false);

        ContactInfoDto contact = contactAccessService.resolveContact(p.getEntityRef().entityType(), p.getEntityRef().entityId()).orElse(null);
        if (contact == null) return this;

        if (!isBlank(contact.phone())) add(buildPhoneRow(contact.phone()));
        if (!isBlank(contact.telegram())) add(buildDeepLinkRow("contact-reveal-telegram", VaadinIcon.PAPERPLANE,
                getValue(CONTACT_REVEAL_BUTTON_TELEGRAM), ContactChannel.TELEGRAM, "https://t.me/" + contact.telegram()));
        if (!isBlank(contact.viber())) add(buildDeepLinkRow("contact-reveal-viber", VaadinIcon.MOBILE,
                getValue(CONTACT_REVEAL_BUTTON_VIBER), ContactChannel.VIBER, "viber://chat?number=" + contact.viber()));

        setVisible(getComponentCount() > 0);
        return this;
    }

    private Div buildPhoneRow(String phone) {
        Div row = new Div();
        row.addClassName("contact-reveal-row");
        row.addClassName("contact-reveal-phone");

        var button = new Button(getValue(CONTACT_REVEAL_BUTTON_SHOW_PHONE), VaadinIcon.PHONE.create());
        button.addClickListener(_ -> {
            if (!checkAllowed()) return;
            contactAccessService.recordView(params.getEntityRef().entityType(), params.getEntityRef().entityId(), ContactChannel.PHONE, access.getCurrentUserId());
            Span revealed = new Span(phone);
            revealed.addClassName("contact-reveal-phone-value");
            row.removeAll();
            row.add(revealed);
        });
        row.add(button);
        return row;
    }

    private Div buildDeepLinkRow(String cssClass, VaadinIcon icon, String label, ContactChannel channel, String deepLinkUrl) {
        Div row = new Div();
        row.addClassName("contact-reveal-row");
        row.addClassName(cssClass);

        var button = new Button(label, icon.create());
        button.addClickListener(_ -> {
            if (!checkAllowed()) return;
            contactAccessService.recordView(params.getEntityRef().entityType(), params.getEntityRef().entityId(), channel, access.getCurrentUserId());
            UI.getCurrent().getPage().open(deepLinkUrl, "_blank");
        });
        row.add(button);
        return row;
    }

    private boolean checkAllowed() {
        try {
            rateLimiter.checkAndRecord(access.getCurrentUserId());
            return true;
        } catch (TooManyAttemptsException ex) {
            notificationService.error(CONTACT_REVEAL_NOTIFICATION_RATE_LIMITED);
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
