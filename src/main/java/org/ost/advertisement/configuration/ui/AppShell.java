package org.ost.advertisement.configuration.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

@PWA(name = "Advertisement App", shortName = "AdvertApp")
@Theme(themeClass = Lumo.class)
public class AppShell implements AppShellConfigurator {
}
