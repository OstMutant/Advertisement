package org.ost.marketplace.ui.views.main.tabs.advertisements;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

@Route("ads")
public class AdvertisementDeepLinkView extends Div implements HasUrlParameter<Long> {

    @Override
    public void setParameter(BeforeEvent event, Long adId) {
        VaadinSession.getCurrent().setAttribute(PendingAdvertisementDeepLink.class, new PendingAdvertisementDeepLink(adId));
        event.forwardTo("");
    }
}
