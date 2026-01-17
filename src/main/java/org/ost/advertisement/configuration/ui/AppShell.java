package org.ost.advertisement.configuration.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;

@Theme("my-app")
@PWA(name = "Advertisement App", shortName = "AdvertApp")
public class AppShell implements AppShellConfigurator {
}
