package org.ost.advertisement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import java.util.HashMap;
import java.util.Map;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.repository.UserRepository;
import org.ost.advertisement.services.SecurityService;
import org.ost.advertisement.ui.utils.SessionUtil;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.advertisements.AdvertisementsView;
import org.ost.advertisement.ui.views.header.HeaderBar;
import org.ost.advertisement.ui.views.users.UsersView;

@Route("")
public class MainView extends VerticalLayout {

	private final HeaderBar headerBar;
	private final SecurityService securityService;

	public MainView(HeaderBar headerBar, SecurityService securityService, UserRepository userRepository,
					AdvertisementRepository advertisementRepository) {
		this.headerBar = headerBar;
		this.securityService = securityService;

		TimeZoneUtil.detectTimeZone();

		setSizeFull();
		setPadding(false);
		setSpacing(false);
		setAlignItems(Alignment.STRETCH);

		add(headerBar);

		Tab advertisementTab = new Tab("Advertisements");
		Tabs tabs = new Tabs(advertisementTab);
		tabs.setSelectedTab(advertisementTab);
		add(tabs);

		AdvertisementsView advertisementsView = new AdvertisementsView(advertisementRepository);
		advertisementsView.setSizeFull();
		advertisementsView.setVisible(true);

		Map<Tab, Component> tabsToPages = new HashMap<>();
		tabsToPages.put(advertisementTab, advertisementsView);

		Div pages = new Div(advertisementsView);
		pages.setSizeFull();
		add(pages);

		if (securityService.isAdmin(SessionUtil.getCurrentUser())) {
			UsersView usersView = new UsersView(userRepository);
			usersView.setSizeFull();
			usersView.setVisible(false);

			Tab usersTab = new Tab("Users");
			tabs.add(usersTab);
			tabsToPages.put(usersTab, usersView);
			pages.add(usersView);
		}

		tabs.addSelectedChangeListener(event -> {
			tabsToPages.values().forEach(page -> page.setVisible(false));
			tabsToPages.get(tabs.getSelectedTab()).setVisible(true);
		});
	}

}
