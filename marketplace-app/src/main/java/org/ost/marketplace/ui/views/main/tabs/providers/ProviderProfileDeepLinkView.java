package org.ost.marketplace.ui.views.main.tabs.providers;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

/** Deep-link route for a provider profile, e.g. {@code /providers/42-ivan-plytochnyk} -- the optional slug is ignored, only the leading numeric id is used for lookup. */
@Route("providers")
public class ProviderProfileDeepLinkView extends Div implements HasUrlParameter<String> {

    @Override
    public void setParameter(BeforeEvent event, String idAndSlug) {
        Long providerId = parseLeadingId(idAndSlug);
        VaadinSession.getCurrent().setAttribute(PendingProviderProfileDeepLink.class, new PendingProviderProfileDeepLink(providerId));
        event.forwardTo("");
    }

    private static Long parseLeadingId(String idAndSlug) {
        int dashIndex = idAndSlug.indexOf('-');
        String idPart = dashIndex >= 0 ? idAndSlug.substring(0, dashIndex) : idAndSlug;
        try {
            return Long.valueOf(idPart);
        } catch (NumberFormatException _) {
            throw new NotFoundException("Invalid provider id: " + idAndSlug);
        }
    }
}
