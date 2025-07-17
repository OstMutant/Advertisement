package org.ost.advertisement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.HashMap;
import java.util.Map;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.header.HeaderBar;
import org.ost.advertisement.ui.views.advertisements.AdvertisementsView;
import org.ost.advertisement.ui.views.users.UsersView;

@SpringComponent
@UIScope
@Route("")
public class MainView extends VerticalLayout {

	private final Tabs tabs;
	private final Div pages;

	private final HeaderBar headerBar;
	private final AdvertisementsView advertisementsView;
	private final UsersView usersView;

	public MainView(HeaderBar headerBar, AdvertisementsView advertisementsView, UsersView usersView) {
		this.headerBar = headerBar;
		this.advertisementsView = advertisementsView;
		this.usersView = usersView;

		TimeZoneUtil.detectTimeZone();

		setSizeFull();
		setPadding(false);
		setSpacing(false);
		setAlignItems(Alignment.STRETCH);

		add(headerBar);

		Tab advertisementTab = new Tab("Advertisements");
		Tab usersTab = new Tab("Users");

		tabs = new Tabs(advertisementTab, usersTab);
		add(tabs);

		advertisementsView.setSizeFull();
		usersView.setSizeFull();
		usersView.setVisible(false);

		pages = new Div(advertisementsView, usersView);
		pages.setSizeFull();
		add(pages);

		Map<Tab, Component> tabsToPages = new HashMap<>();
		tabsToPages.put(advertisementTab, advertisementsView);
		tabsToPages.put(usersTab, usersView);

		tabs.addSelectedChangeListener(event -> {
			tabsToPages.values().forEach(page -> page.setVisible(false));
			tabsToPages.get(tabs.getSelectedTab()).setVisible(true);
		});

		tabs.setSelectedTab(advertisementTab);
		advertisementsView.setVisible(true);
	}
}
