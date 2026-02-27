package org.ost.advertisement.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class MainView extends VerticalLayout {

    private final transient HeaderBar          headerBar;
    private final transient AdvertisementsView advertisementsView;
    private final transient UserView           usersView;
    private final transient AccessEvaluator    access;
    private final transient I18nService        i18n;

    @PostConstruct
    public void init() {
        TimeZoneUtil.detectTimeZone();

        addClassName("main-view-root");
        setSizeFull();

        Map<Tab, Component> tabsToPages = new HashMap<>();

        Tab  advertisementTab = new Tab(i18n.get(MAIN_TAB_ADVERTISEMENTS));
        Tabs tabs             = buildTabs(advertisementTab);
        Div  pages            = buildPages();

        tabsToPages.put(advertisementTab, advertisementsView);

        if (access.canView()) {
            Tab usersTab = new Tab(i18n.get(MAIN_TAB_USERS));
            tabs.add(usersTab);
            pages.add(usersView);
            tabsToPages.put(usersTab, usersView);
            usersView.setVisible(false);
        }

        tabs.addSelectedChangeListener(_ -> {
            tabsToPages.values().forEach(page -> page.setVisible(false));
            tabsToPages.get(tabs.getSelectedTab()).setVisible(true);
        });

        headerBar.addClassName("main-header");
        add(headerBar, tabs, pages);
    }

    private Tabs buildTabs(Tab initialTab) {
        Tabs tabs = new Tabs(initialTab);
        tabs.setSelectedTab(initialTab);
        tabs.addClassName("main-tabs");
        return tabs;
    }

    private Div buildPages() {
        Div pages = new Div(advertisementsView);
        pages.setSizeFull();
        pages.addClassName("main-pages");
        advertisementsView.setVisible(true);
        return pages;
    }
}