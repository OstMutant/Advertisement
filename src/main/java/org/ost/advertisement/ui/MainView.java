package org.ost.advertisement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.advertisements.AdvertisementsView;
import org.ost.advertisement.ui.views.header.HeaderBar;
import org.ost.advertisement.ui.views.users.UserView;

import java.util.HashMap;
import java.util.Map;

import static org.ost.advertisement.constants.I18nKey.MAIN_TAB_ADVERTISEMENTS;
import static org.ost.advertisement.constants.I18nKey.MAIN_TAB_USERS;

@Route("")
public class MainView extends VerticalLayout {

    public MainView(HeaderBar headerBar, AdvertisementsView advertisementsView, UserView usersView,
                    AccessEvaluator access, I18nService i18n) {

        TimeZoneUtil.detectTimeZone();

        setSizeFull();
        addClassName("main-view-root");

        // header
        add(headerBar);
        headerBar.addClassName("main-header");

        // tabs
        Tab advertisementTab = new Tab(i18n.get(MAIN_TAB_ADVERTISEMENTS));
        Tabs tabs = new Tabs(advertisementTab);
        tabs.setSelectedTab(advertisementTab);
        tabs.addClassName("main-tabs");
        add(tabs);

        advertisementsView.setVisible(true);

        Map<Tab, Component> tabsToPages = new HashMap<>();
        tabsToPages.put(advertisementTab, advertisementsView);

        // pages container
        Div pages = new Div(advertisementsView);
        pages.setSizeFull();
        pages.addClassName("main-pages");
        add(pages);

        if (access.canView()) {
            usersView.setVisible(false);

            Tab usersTab = new Tab(i18n.get(MAIN_TAB_USERS));
            tabs.add(usersTab);
            tabsToPages.put(usersTab, usersView);
            pages.add(usersView);
        }

        tabs.addSelectedChangeListener(_ -> {
            tabsToPages.values().forEach(page -> page.setVisible(false));
            tabsToPages.get(tabs.getSelectedTab()).setVisible(true);
        });
    }
}
