package org.ost.marketplace.ui.views.main.tabs.referencedata;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;

import java.util.HashMap;
import java.util.Map;

import static org.ost.marketplace.services.i18n.I18nKey.REFERENCE_DATA_TAB_CATEGORIES;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class ReferenceDataView extends VerticalLayout {

    private final I18nService i18n;
    private final TaxonManagementView taxonManagementView;

    @PostConstruct
    protected void init() {
        addClassName("reference-data-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        Tab categoriesTab = new Tab(i18n.get(REFERENCE_DATA_TAB_CATEGORIES));
        Tabs subTabs = new Tabs(categoriesTab);
        subTabs.addClassName("reference-data-sub-tabs");

        Map<Tab, Component> tabsToPages = new HashMap<>();
        tabsToPages.put(categoriesTab, taxonManagementView);

        Div pages = new Div(taxonManagementView);
        pages.setSizeFull();
        pages.addClassName("reference-data-pages");

        subTabs.addSelectedChangeListener(_ -> {
            tabsToPages.values().forEach(p -> p.setVisible(false));
            tabsToPages.get(subTabs.getSelectedTab()).setVisible(true);
        });

        add(subTabs, pages);
    }
}
