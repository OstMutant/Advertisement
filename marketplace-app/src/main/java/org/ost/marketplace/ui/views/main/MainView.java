package org.ost.marketplace.ui.views.main;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.Route;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.elements.fields.QueryNumberField;
import org.ost.marketplace.ui.views.main.header.HeaderBar;
import org.ost.marketplace.ui.views.main.tabs.advertisements.AdvertisementsView;
import org.ost.marketplace.ui.views.main.tabs.referencedata.ReferenceDataView;
import org.ost.marketplace.ui.views.main.tabs.timeline.TimelineView;
import org.ost.marketplace.ui.views.main.tabs.users.UserView;
import org.ost.marketplace.ui.query.utils.TimeZoneUtil;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.spi.TaxonPort;

import java.util.HashMap;
import java.util.Map;

import static org.ost.marketplace.services.i18n.I18nKey.MAIN_TAB_ADVERTISEMENTS;
import static org.ost.marketplace.services.i18n.I18nKey.MAIN_TAB_REFERENCE_DATA;
import static org.ost.marketplace.services.i18n.I18nKey.MAIN_TAB_TIMELINE;
import static org.ost.marketplace.services.i18n.I18nKey.MAIN_TAB_USERS;

@Route("")
@RequiredArgsConstructor
@Uses(Notification.class)
@Uses(Icon.class)
@Uses(SvgIcon.class)
@Uses(DatePicker.class)
@Uses(TimePicker.class)
@Uses(QueryDateTimeField.class)
@Uses(NumberField.class)
@Uses(QueryNumberField.class)
public class MainView extends VerticalLayout {

    private final transient HeaderBar headerBar;
    private final transient AdvertisementsView advertisementsView;
    private final transient UserView usersView;
    private final transient TimelineView timelineView;
    private final transient ReferenceDataView referenceDataView;
    private final transient AccessEvaluator access;
    private final transient I18nService i18n;
    private final transient ComponentFactory<TaxonPort> taxonPortFactory;

    @PostConstruct
    public void init() {
        TimeZoneUtil.detectTimeZone();

        addClassName("main-view-root");
        setSizeFull();

        Map<Tab, Component> tabsToPages = new HashMap<>();

        Tab advertisementTab = new Tab(i18n.get(MAIN_TAB_ADVERTISEMENTS));
        Tabs tabs = buildTabs(advertisementTab);
        Div pages = buildPages();

        tabsToPages.put(advertisementTab, advertisementsView);

        if (access.canView()) {
            Tab usersTab = new Tab(i18n.get(MAIN_TAB_USERS));
            tabs.add(usersTab);
            pages.add(usersView);
            tabsToPages.put(usersTab, usersView);
            usersView.setVisible(false);

            Tab timelineTab = new Tab(i18n.get(MAIN_TAB_TIMELINE));
            tabs.add(timelineTab);
            pages.add(timelineView);
            tabsToPages.put(timelineTab, timelineView);
            timelineView.setVisible(false);

            taxonPortFactory.findIfAvailable().ifPresent(_ -> {
                Tab refDataTab = new Tab(i18n.get(MAIN_TAB_REFERENCE_DATA));
                tabs.add(refDataTab);
                pages.add(referenceDataView);
                tabsToPages.put(refDataTab, referenceDataView);
                referenceDataView.setVisible(false);
            });
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